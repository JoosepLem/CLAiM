---
name: code_quality
description: "Review a diff for code quality issues — naming, duplication, module design, abstraction discipline. Returns approved/blocking with actionable feedback."
disable-model-invocation: true
---

Review the given diff for code quality. You are checking whether the implementor followed the code quality bars. Only flag issues that are severe enough to block merging — nitpicks and style preferences don't belong here.

## Input

- **diff** — the full diff produced by the implementor

## What to Check

For each hunk in the diff, scan for these classes of problem:

### Names

- **Mysterious names.** Functions, variables, types, or files whose name doesn't reveal what they do or hold. Bad: `data`, `info`, `handle`, `process`, `tmp`, `Manager`, `Util`. Good: `parseInvoicePdf`, `pendingOrders`, `hashPassword`.
- **Inconsistent with codebase.** A name that breaks the repo's naming convention (e.g. snake_case in a camelCase codebase). Use the surrounding file context to judge.
- **Misleading names.** A name that says one thing but the code does another (e.g. `getUser` that mutates state).

### Duplication

- **Same logic shape appears more than once in the change.** If two or more hunks contain structurally identical logic (same steps, different values), flag it. The implementor should have extracted a shared function.
- **Near-duplicate with existing code.** If the diff reimplements something that already exists elsewhere in the repo, flag it — the implementor should have reused, not rewritten.

### Data & Types

- **Data clumps.** Two or more fields or parameters that always travel together (e.g. `(host, port, timeout)` appearing in multiple function signatures) but aren't bundled into a type.
- **Primitive obsession.** A string or number standing in for a domain concept that deserves its own type — raw strings for emails, currency amounts as bare numbers, status as magic strings.
- **Feature envy.** A method that reaches into another object's data (via getters or property access) more than its own. The method belongs on the other object.

### Module Design

- **Shotgun surgery.** One logical feature requires edits scattered across many unrelated files. The diff has changes in too many places for one coherent change.
- **Divergent change.** One file or module is edited for several unrelated reasons. A single file touched by multiple unrelated changes.
- **Middle man.** A new class or function that mostly just delegates to another without adding value. Cut it.

### Abstraction Discipline

- **Speculative generality.** Abstraction, parameters, hooks, configuration points, or interfaces added for needs the plan doesn't mention. Concrete first, abstract only when a second caller arrives.
- **Refused bequest / needless inheritance.** A subclass that ignores or overrides most of what it inherits. Should use composition.
- **Message chains.** Long navigation chains the caller shouldn't depend on. Flag `a.b().c().d()` patterns.

## What NOT to Flag

- Formatting issues — the `check_format` step handles those
- Lint rule violations — the `check_lint` step handles those
- Missing tests — the `tests` reviewer handles that
- Performance concerns — the `performance` reviewer handles that
- Security issues — the `security` reviewer handles that
- Functional gaps — the `functional` reviewer handles that
- Pre-existing issues in untouched code — only review what changed

## Judgement

Each finding is a judgement call, not a hard rule. Before flagging, ask:
- Does this genuinely make the code harder to understand or change?
- Would fixing this prevent a real bug or maintenance headache?
- Is the fix worth the churn at this stage?

If the answer is "maybe" or "barely", don't flag it. Only flag clear problems.

## Output Format

Return JSON:

```json
{
  "approved": true,
  "blocking": []
}
```

Or when issues are found:

```json
{
  "approved": false,
  "blocking": [
    {
      "file": "src/services/user.ts",
      "line": 42,
      "issue": "Mysterious Name",
      "detail": "Function `process` doesn't reveal what it processes. Rename to `hashAndStorePassword`.",
      "suggestion": "Rename to hashAndStorePassword"
    },
    {
      "file": "src/handlers/auth.ts",
      "line": 12,
      "issue": "Duplicated Code",
      "detail": "The token validation logic in `validateToken` is identical to the one in `refreshToken` at line 38. Extract into a shared `parseAndVerifyToken`.",
      "suggestion": "Extract shared parseAndVerifyToken function"
    }
  ]
}
```

- **`approved`** — `true` if no blocking issues, `false` if any issues found
- **`blocking`** — list of issues. Each issue has `file`, `line` (best guess), `issue` (category name), `detail` (description), `suggestion` (actionable fix)
- If nothing to report, return `approved: true` with an empty `blocking` list

## Before Returning

- [ ] Every `blocking` item references a specific file and line in the diff
- [ ] Every `blocking` item includes an actionable suggestion
- [ ] No items that belong to another reviewer's domain
- [ ] No items for pre-existing code outside the diff
