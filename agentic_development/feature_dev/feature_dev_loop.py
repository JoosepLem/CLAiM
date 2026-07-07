import json
import argparse
import subprocess
import sys
import concurrent.futures
from datetime import datetime, timezone
from pathlib import Path

try:
    from .agent import OpenCodeCLIAgent, set_log_file, _tee, PipelineAbortError, DIM, RESET, BOLD, MAGENTA, CYAN, GREEN, YELLOW, RED
except ImportError:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from agent import OpenCodeCLIAgent, set_log_file, _tee, PipelineAbortError, DIM, RESET, BOLD, MAGENTA, CYAN, GREEN, YELLOW, RED

MAX_ITERS = 5

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

REVIEWERS = ["code_quality", "performance", "tests", "security", "functional"]

CHECKS = ["format", "lint", "typecheck", "build", "tests"]


def merge_feedback(reviews):
    feedback = []
    for r in reviews:
        if not r.get("approved", False):
            feedback.extend(r.get("blocking", []))
    return feedback


def _parse_result(result):
    if isinstance(result, (dict, list)):
        return result
    return json.loads(result)


def _generate_git_diff():
    poc = REPO_ROOT / "proof_of_concept"
    try:
        for cmd in [["git", "diff", "--cached"], ["git", "diff"]]:
            result = subprocess.run(cmd, capture_output=True, text=True,
                                    cwd=str(poc), timeout=10)
            diff = result.stdout.strip()
            if diff:
                return diff
        subprocess.run(["git", "add", "-N", "."], capture_output=True,
                       cwd=str(poc), timeout=10)
        result = subprocess.run(["git", "diff"], capture_output=True, text=True,
                                cwd=str(poc), timeout=10)
        return result.stdout.strip() or None
    except Exception:
        return None


def _probe_implementation(context):
    poc = REPO_ROOT / "proof_of_concept"
    sentinel_files = [
        "src/main/kotlin/ee/claimai/auth/AuthController.kt",
        "src/main/kotlin/ee/claimai/security/JwtService.kt",
        "src/main/kotlin/ee/claimai/config/SecurityConfig.kt",
        "src/main/kotlin/ee/claimai/user/UserRepository.kt",
        "src/main/resources/templates/login.html",
        "src/main/resources/templates/dashboard.html",
    ]
    existing = [f for f in sentinel_files if (poc / f).exists()]
    if len(existing) == 0:
        return False
    ratio = len(existing) / len(sentinel_files)
    _tee(f"  {DIM}pre-flight: {len(existing)}/{len(sentinel_files)} sentinel files exist ({ratio:.0%}){RESET}")
    if ratio < 0.5:
        return False
    try:
        result = subprocess.run(
            ["./gradlew", "compileKotlin", "compileTestKotlin"],
            capture_output=True, text=True, cwd=str(poc), timeout=120
        )
        if result.returncode == 0:
            _tee(f"  {GREEN}✓ pre-flight: compilation passes — implementation already exists{RESET}")
            return True
    except Exception:
        pass
    return False


def _run_review_cycle(agent, diff, plan):
    reviews = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(REVIEWERS)) as executor:
        review_futures = {
            executor.submit(
                lambda r=r: _parse_result(agent.run(r, {"diff": diff, "plan": plan}))
            ): r for r in REVIEWERS
        }
        for future in concurrent.futures.as_completed(review_futures):
            reviews.append(future.result())

    checks = []
    for c in CHECKS:
        checks.append(_parse_result(agent.run("check_" + c, {})))

    checks_passed = sum(1 for c in checks if isinstance(c, dict) and c.get("passed"))
    reviews_approved = sum(1 for r in reviews if r.get("approved"))

    _iter_footer(reviews_approved, len(REVIEWERS), checks_passed, len(CHECKS))

    feedback = merge_feedback(reviews)
    success = all(r.get("approved", False) for r in reviews) and all(
        isinstance(c, dict) and c.get("passed") for c in checks
    )
    return success, reviews, checks, feedback
MAGENTA = "\033[35m"
CYAN = "\033[36m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RED = "\033[31m"
RESET = "\033[0m"


def _banner(text: str):
    _tee(f"\n{BOLD}{MAGENTA}{'═' * 60}{RESET}")
    _tee(f"{BOLD}{MAGENTA}  {text}{RESET}")
    _tee(f"{BOLD}{MAGENTA}{'═' * 60}{RESET}\n")


def _iter_header(iteration: int, max_iters: int):
    _tee(f"\n{BOLD}{CYAN}┌─ Iteration {iteration + 1}/{max_iters} {'─' * 44}{RESET}")


def _iter_footer(approved: int, total: int, checks_passed: int, total_checks: int):
    bar = "├"
    _tee(f"{BOLD}{CYAN}{bar}─ Reviewers: {approved}/{total} approved  |  Checks: {checks_passed}/{total_checks} passed{RESET}\n")


def _final_result(result: dict):
    status = result.get("status", "UNKNOWN")
    if status == "DELEGATED":
        color = YELLOW
    elif status == "FAILED":
        color = RED
    else:
        color = GREEN
    _tee(f"\n{BOLD}{color}  RESULT: {status}{RESET}")
    if status == "DELEGATED":
        _tee(f"  {YELLOW}reason: {result.get('reason', '')}{RESET}")
    if status == "FAILED":
        fb = result.get("last_feedback", [])
        _tee(f"  {RED}{len(fb)} unresolved blocking issues{RESET}")
    _tee("")


def run_pipeline(agent, plan: str):

    global MAX_ITERS

    original_plan = plan

    context = _parse_result(agent.run("context_builder", plan))
    feedback = []
    consecutive_no_diff = 0

    preflight_diff = None
    if _probe_implementation(context):
        diff = _generate_git_diff()
        if diff:
            _tee(f"  {GREEN}✓ implementation already exists — jumping to review with {len(diff)}-char git diff{RESET}")
            preflight_diff = diff

    for i in range(MAX_ITERS):

        _iter_header(i, MAX_ITERS)

        if preflight_diff is not None:
            diff = preflight_diff
            plan_modifications = ""
            preflight_diff = None
        else:
            result = agent.run("implement", {
                "plan": plan,
                "context": context,
                "feedback": feedback,
            })

            impl_output = _parse_result(result)

            if impl_output.get("status") == "delegated_to_human":
                d = {
                    "status": "DELEGATED",
                    "reason": impl_output.get("reason", "unspecified ambiguity"),
                    "iteration": i + 1,
                    "feedback": feedback,
                }
                _final_result(d)
                return d

            plan_modifications = impl_output.get("plan_modifications", "")
            if plan_modifications:
                plan = plan.rstrip() + "\n\n" + plan_modifications.strip()
                _tee(f"  {MAGENTA}📝 plan updated with {len(plan_modifications)} chars of deviations{RESET}")

            diff = impl_output.get("diff")
            if not diff:
                if plan_modifications:
                    _tee(f"  {YELLOW}⚠ implementor produced plan_modifications but no diff — "
                         f"recovering from git{RESET}")
                    diff = _generate_git_diff()
                    if diff:
                        _tee(f"  {GREEN}✓ recovered diff from git ({len(diff)} chars){RESET}")

                if not diff:
                    consecutive_no_diff += 1
                    if consecutive_no_diff >= 2:
                        fr = {
                            "status": "FAILED",
                            "reason": "loop_deadlock",
                            "detail": "Implementor failed to produce a diff after 2 attempts. "
                                      "Check the implementor output format — expected JSON with "
                                      "'status': 'diff' and 'diff': '<git diff>' fields.",
                        }
                        _final_result(fr)
                        return fr
                    _tee(f"  {RED}✗ implementor returned no diff — cannot review, retrying{RESET}")
                    feedback = [{
                        "file": "N/A", "line": 0,
                        "issue": "Output Format",
                        "detail": "Your JSON output did not include a 'diff' field. "
                                  "Return a JSON object with 'status': 'diff', "
                                  "'diff': '<git diff of your changes>', "
                                  "'summary': '<what you built>'.",
                        "suggestion": "Return the correct JSON format with 'diff' field containing your git diff."
                    }]
                    continue
                consecutive_no_diff = 0

        success, reviews, checks, feedback = _run_review_cycle(agent, diff, plan)

        if success:

            report = {
                "plan": plan,
                "original_plan": original_plan,
                "iterations": i + 1,
                "reviews": reviews,
                "checks": checks,
            }

            agent.run("doc_agent", report)

            dr = {"status": "SUCCESS", "iterations": i + 1}
            _final_result(dr)
            return report

    fr = {
        "status": "FAILED",
        "reason": "max_iterations",
        "last_feedback": feedback,
    }
    _final_result(fr)
    return fr


def main():
    global MAX_ITERS

    parser = argparse.ArgumentParser(
        description="Feature development loop — implement, review, check, iterate.",
    )
    parser.add_argument(
        "--plan", "-p",
        required=True,
        help="Feature spec / PRD to implement (string or path to a .md/.txt file)",
    )
    parser.add_argument(
        "--model", "-m",
        help="Model to use in provider/model format (e.g. deepseek/deepseek-v4-pro)",
    )
    parser.add_argument(
        "--variant", "-v",
        help="Model variant for reasoning effort (e.g. low, medium, high)",
    )
    parser.add_argument(
        "--opencode-bin",
        default="opencode",
        help="Path to the opencode binary (default: opencode on PATH)",
    )
    parser.add_argument(
        "--max-iters", "-n",
        type=int,
        default=MAX_ITERS,
        help=f"Maximum implement-review iterations (default: {MAX_ITERS})",
    )
    parser.add_argument(
        "--log", "-l",
        help="Log file path (default: agentic_development/logs/<timestamp>.log)",
    )

    args = parser.parse_args()

    plan_path = Path(args.plan)
    if plan_path.exists() and plan_path.suffix in (".md", ".txt"):
        plan = plan_path.read_text()
        plan_label = str(plan_path)
    else:
        plan = args.plan
        plan_label = "inline plan"

    MAX_ITERS = args.max_iters

    repo_root = Path(__file__).resolve().parent.parent.parent
    log_path = Path(args.log) if args.log else repo_root / "agentic_development" / "logs" / f"{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}.log"
    set_log_file(log_path)
    _tee(f"{DIM}# Log: {log_path}{RESET}")

    _banner(f"Feature Dev Loop  |  plan: {plan_label}  |  model: {args.model or 'default'}")

    agent = OpenCodeCLIAgent(opencode_bin=args.opencode_bin, model=args.model, variant=args.variant)

    try:
        result = run_pipeline(agent, plan)
    except PipelineAbortError as e:
        _tee(f"\n{BOLD}{RED}═══ PIPELINE ABORTED ═══{RESET}")
        _tee(f"  {RED}skill: {e.skill}{RESET}")
        _tee(f"  {RED}reason: {e.reason}{RESET}")
        if e.detail:
            _tee(f"  {DIM}{e.detail}{RESET}")
        _tee("")
        result = {"status": "ABORTED", "skill": e.skill, "reason": e.reason, "detail": e.detail}
        _tee(json.dumps(result, indent=2))
        return 1

    _tee(json.dumps(result, indent=2))
    return 0 if result.get("status") != "FAILED" else 1


if __name__ == "__main__":
    sys.exit(main())
