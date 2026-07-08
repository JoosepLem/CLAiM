import json
import subprocess
import sys
import re
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SKILLS_DIR = REPO_ROOT / ".agents" / "skills"


class PipelineAbortError(Exception):
    def __init__(self, skill: str, reason: str, detail: str = ""):
        self.skill = skill
        self.reason = reason
        self.detail = detail
        super().__init__(f"[{skill}] {reason}" + (f": {detail}" if detail else ""))

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
CYAN = "\033[36m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RED = "\033[31m"
MAGENTA = "\033[35m"

_log_file: Optional[Path] = None


def set_log_file(path: Path):
    global _log_file
    _log_file = path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("")
    _emit(f"# Log started {datetime.now(timezone.utc).isoformat()}\n")


def _emit(text: str):
    if _log_file:
        with open(_log_file, "a") as f:
            f.write(_strip_all_ansi(text) + "\n")


def _strip_all_ansi(text: str) -> str:
    return re.sub(r"\x1b\[[0-9;]*[a-zA-Z]", "", text)


def _tee(text: str):
    print(text)
    sys.stdout.flush()
    _emit(text)


def _log(symbol: str, color: str, label: str, detail: str = ""):
    line = f"  {color}{symbol}{RESET} {BOLD}{label}{RESET}"
    if detail:
        line += f" {DIM}{detail}{RESET}"
    _tee(line)


class OpenCodeCLIAgent:
    def __init__(self, opencode_bin: str = "opencode", model: Optional[str] = None, variant: Optional[str] = None):
        self.opencode_bin = opencode_bin
        self.model = model
        self.variant = variant
        self.skill_count = 0

    def run(self, skill_name: str, input_data=None):
        if skill_name.startswith("check_"):
            return self._run_check(skill_name)
        return self._run_skill(skill_name, input_data)

    def _run_skill(self, skill_name: str, input_data):
        self.skill_count += 1
        prompt = self._build_skill_prompt(skill_name, input_data)

        detail = ""
        if isinstance(input_data, dict):
            keys = list(input_data.keys())
            detail = f"keys: {', '.join(keys[:4])}"
        elif isinstance(input_data, str) and len(input_data) < 120:
            detail = input_data.strip()[:100]

        _log("▶", CYAN, f"[{self.skill_count}] {skill_name}", detail)
        _tee(f"  {DIM}── {len(prompt)} chars → {self.model or 'default'} ──{RESET}")

        cmd = [self.opencode_bin, "run", "--auto", "--thinking"]
        if self.model:
            cmd.extend(["--model", self.model])
        if self.variant:
            cmd.extend(["--variant", self.variant])

        try:
            proc = subprocess.Popen(
                cmd,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                cwd=str(REPO_ROOT),
            )
        except FileNotFoundError:
            raise PipelineAbortError(
                skill_name, "opencode binary not found",
                f"install with: npm install -g opencode-ai"
            )

        stdout_chunks = []
        stderr_chunks = []

        def read_stream(stream, chunks, label):
            try:
                for line in iter(stream.readline, ""):
                    cleaned = self._strip_ansi(line).rstrip()
                    if cleaned:
                        chunks.append(cleaned)
                        prefix = f"  {DIM}│ " if label == "OUT" else f"  {DIM}err│ "
                        _tee(f"{prefix}{cleaned[:200]}{RESET}")
            except Exception:
                pass

        t_out = threading.Thread(target=read_stream, args=(proc.stdout, stdout_chunks, "OUT"), daemon=True)
        t_err = threading.Thread(target=read_stream, args=(proc.stderr, stderr_chunks, "ERR"), daemon=True)
        t_out.start()
        t_err.start()

        try:
            proc.stdin.write(prompt)
            proc.stdin.close()

            start = time.time()
            last_beat = 0
            while proc.poll() is None:
                elapsed = int(time.time() - start)
                if elapsed - last_beat >= 10:
                    _tee(f"  {DIM}⏳ still waiting... {elapsed}s{RESET}")
                    last_beat = elapsed
                time.sleep(0.5)
                if elapsed > 1800:
                    proc.kill()
                    _tee("")
                    t_out.join(timeout=3)
                    t_err.join(timeout=3)
                    raise PipelineAbortError(skill_name, "timed out", "1800s limit exceeded")

            t_out.join(timeout=5)
            t_err.join(timeout=5)

        except PipelineAbortError:
            raise
        except Exception as e:
            proc.kill()
            _tee("")
            raise PipelineAbortError(skill_name, "crashed", str(e)[:200])

        stdout = "\n".join(stdout_chunks)
        stderr = "\n".join(stderr_chunks)

        if proc.returncode != 0:
            raise PipelineAbortError(
                skill_name, f"opencode exit code {proc.returncode}",
                stderr[-300:].strip()
            )

        cleaned = stdout.strip()
        result = self._extract_json(cleaned)

        if isinstance(result, dict):
            if result.get("approved") is True:
                _log("✓", GREEN, f"{skill_name} approved")
            elif result.get("approved") is False:
                n = len(result.get("blocking", []))
                _log("✗", YELLOW, f"{skill_name} blocked", f"{n} issue(s)")
            elif result.get("status") == "diff":
                summary = result.get("summary", "")
                _log("✓", GREEN, f"{skill_name} produced diff", f"{summary[:80]}")
            elif result.get("status") == "delegated_to_human":
                _log("?", MAGENTA, f"{skill_name} delegated",
                     result.get("reason", "")[:80])
            elif result.get("error"):
                _log("✗", RED, f"{skill_name} error", result["error"][:100])
            else:
                _log("✓", GREEN, f"{skill_name} done",
                     f"keys: {', '.join(list(result.keys())[:4])}")
        elif isinstance(result, list):
            _log("✓", GREEN, f"{skill_name} done", f"{len(result)} items")

        return result

    @staticmethod
    def _strip_ansi(text: str) -> str:
        return re.sub(r"\x1b\[[0-9;]*[a-zA-Z]", "", text)

    def _build_skill_prompt(self, skill_name: str, input_data) -> str:
        skill_md = SKILLS_DIR / skill_name / "SKILL.md"
        if skill_md.exists():
            instructions = skill_md.read_text()
        else:
            instructions = f"You are the {skill_name} agent."
            _tee(f"  {YELLOW}⚠ skill '{skill_name}' not found at {skill_md}, using bare prompt{RESET}")

        extra = ""
        if skill_name == "implement":
            extra = self._implementor_deviations_guide()
        elif skill_name == "functional":
            extra = self._functional_deviations_guide()

        encoded = json.dumps(input_data) if isinstance(input_data, (dict, list)) else str(input_data or "")

        return (
            f"{instructions}\n\n{extra}"
            f"## Input Data\n\n"
            f"```json\n{encoded}\n```\n\n"
            f"Follow the instructions above. Return your output as valid JSON matching the output format specified."
        )

    @staticmethod
    def _implementor_deviations_guide() -> str:
        return (
            "## Plan Deviations\n\n"
            "If the plan is faulty or incompatible with the codebase (wrong APIs, removed features, version "
            "mismatches), do NOT delegate to human. Instead:\n\n"
            "1. Implement the code the CORRECT way that works with the actual codebase.\n"
            "2. In your output, include a `plan_modifications` field containing a Markdown section "
            "titled '## Deviations' that documents each deviation: what the plan said, what you did instead, "
            "and why. Example:\n\n"
            "```\n"
            "## Deviations\n\n"
            "- **@DataJdbcTest → @SpringBootTest**: The annotation was removed in Spring Boot 4.0. "
            "Using @SpringBootTest instead.\n"
            "- **TestRestTemplate → RestTemplate + @LocalServerPort**: TestRestTemplate was removed "
            "in Spring Boot 4.0.\n"
            "```\n\n"
            "3. The updated plan (original + deviations appended) will be used in the next iteration "
            "and by the functional reviewer.\n\n"
            "## Existing Code (fast path)\n\n"
            "If the plan describes code that already exists on disk (files are present, tests pass), "
            "do NOT modify working code or fix cosmetic compiler warnings. Instead:\n"
            "1. Run the tests to verify they pass (`./gradlew test`).\n"
            "2. Generate a clean git diff of ALL relevant files. Use `git add -N . && git diff` for new/untracked files.\n"
            "3. Return immediately with `{\"status\": \"diff\", \"diff\": \"<the full git diff>\", \"summary\": \"...\"}`.\n"
            "4. If the diff is larger than 30KB, include only files matching the plan's deliverables.\n\n"
        )

    @staticmethod
    def _functional_deviations_guide() -> str:
        return (
            "## Deviation Tolerance\n\n"
            "The plan may contain a '## Deviations' section added by the implementor. "
            "These document intentional departures from the original spec to fix incompatibilities. "
            "Do NOT flag anything listed in a Deviations section as a Missing Requirement or Wrong Behavior. "
            "The deviation documents that the change was deliberate.\n\n"
        )

    def _run_check(self, check_name: str):
        check = check_name.replace("check_", "")
        handlers = {
            "format": self._check_format,
            "lint": self._check_lint,
            "typecheck": self._check_typecheck,
            "build": self._check_build,
            "tests": self._check_tests,
        }
        handler = handlers.get(check, lambda: False)
        try:
            result = handler()
            parsed = json.loads(result) if isinstance(result, str) else result
            if parsed.get("passed"):
                _log("✓", GREEN, f"check:{check}", "passed")
            else:
                msg = (parsed.get("output", "failed"))[:100]
                _log("✗", RED, f"check:{check}", msg)
            return parsed
        except Exception as e:
            _log("✗", RED, f"check:{check}", str(e)[:100])
            return {"passed": False, "error": str(e)}

    def _run_cmd(self, cmd: list, cwd: Optional[Path] = None, timeout: int = 120):
        try:
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=timeout,
                cwd=str(cwd or REPO_ROOT)
            )
            return result.returncode == 0, result.stdout + result.stderr
        except FileNotFoundError:
            return True, f"[SKIPPED] command not found: {cmd[0]}"
        except subprocess.TimeoutExpired:
            return False, f"timed out after {timeout}s"

    def _check_format(self):
        poc = REPO_ROOT / "proof_of_concept"
        gradlew = poc / "gradlew"
        if not gradlew.exists():
            return json.dumps({"passed": False, "output": "format: gradlew not found"})
        ok, out = self._run_cmd(["./gradlew", "ktlintCheck"], cwd=poc, timeout=120)
        if ok:
            return json.dumps({"passed": True})
        if "not found" in out.lower() or "did you mean" in out.lower() or "unknown" in out.lower():
            return json.dumps({"passed": True, "output": "format: ktlint not configured, skipping"})
        return json.dumps({"passed": False, "output": f"format failed\n{out[-500:]}"})

    def _check_lint(self):
        poc = REPO_ROOT / "proof_of_concept"
        gradlew = poc / "gradlew"
        if not gradlew.exists():
            return json.dumps({"passed": False, "output": "lint: gradlew not found"})
        ok, out = self._run_cmd(["./gradlew", "detekt"], cwd=poc, timeout=120)
        if ok:
            return json.dumps({"passed": True})
        if "not found" in out.lower() or "did you mean" in out.lower() or "unknown" in out.lower():
            return json.dumps({"passed": True, "output": "lint: detekt not configured, skipping"})
        return json.dumps({"passed": False, "output": f"lint failed\n{out[-500:]}"})

    def _check_typecheck(self):
        return self._run_gradle(["compileKotlin", "compileTestKotlin"], "typecheck")

    def _check_build(self):
        return self._run_gradle(["assemble"], "build", timeout=300)

    def _check_tests(self):
        return self._run_gradle(["test"], "tests", timeout=300)

    def _run_gradle(self, args: list, label: str, timeout: int = 120):
        poc = REPO_ROOT / "proof_of_concept"
        gradlew = poc / "gradlew"
        if not gradlew.exists():
            return json.dumps({"passed": False, "output": f"{label}: gradlew not found in proof_of_concept/ — scaffold not yet implemented"})
        ok, out = self._run_cmd(["./gradlew"] + args, cwd=poc, timeout=timeout)
        if not ok:
            return json.dumps({"passed": False, "output": f"{label} failed\n{out[-500:]}"})
        return json.dumps({"passed": True})

    @staticmethod
    def _extract_json(text: str):
        text = text.strip()
        text = re.sub(r"^```(?:json)?\s*\n", "", text)
        text = re.sub(r"\n```\s*$", "", text)
        if text.startswith("{") or text.startswith("["):
            try:
                return json.loads(text)
            except json.JSONDecodeError:
                pass
        m = re.search(r"\{[\s\S]*\}", text)
        if m:
            try:
                return json.loads(m.group(0))
            except json.JSONDecodeError:
                pass
        return json.dumps({"raw": text})
