---
name: functional
description: "Review a diff against the plan — are all requirements implemented, is there scope creep, does the behavior match the spec. Returns approved/blocking with actionable feedback."
disable-model-invocation: true
---

Review the given diff against the plan. You are checking whether the implementation is functionally correct and complete. You need both the diff and the plan — the diff alone cannot tell you if the right thing was built.

## Input

- **diff** — the full diff produced by the implementor
- **plan** — the feature spec / PRD that was given as input

## What to Check

### Requirements Coverage

- **Every requirement has a matching implementation.** Walk through each requirement in the plan one by one. For each, find the code in the diff that fulfills it. Flag any requirement that has no code behind it.
- **Acceptance criteria are testable.** For each acceptance criterion in the plan ("user can reset password", "export produces valid CSV"), verify the implementation includes the full flow — not just the happy path, but the complete lifecycle.
- **Partial implementations.** A requirement that is half-done is a bug. If the plan says "users receive a confirmation email after signup" and the diff has the signup code but no email sending, flag it as incomplete.

### Scope Discipline

- **No scope creep.** Flag any feature, endpoint, behavior, or code path in the diff that isn't traceable to a requirement in the plan. Added a new API endpoint not in the plan? Flag it. Added a configuration option no one asked for? Flag it.
- **Genuinely helpful additions.** Sometimes an implementation naturally needs a small helper or utility that's not explicitly in the plan. Judge pragmatically: a `formatDate` utility used by the feature is fine. A new admin dashboard page is not.
- **Graceful extras that don't add surface area.** Internal refactoring, extracting a helper, adding a type — fine, as long as it doesn't create new public API surface or user-facing behavior.

### Behavioral Correctness

- **The implementation does what the spec says, not what you'd assume.** Read the spec requirement, then read the code. Does the code actually deliver that behavior? Don't fill in gaps — if the plan is ambiguous, flag the ambiguity.
- **Error behavior matches the spec.** If the plan specifies error responses, HTTP status codes, or failure modes, verify they're implemented correctly. If the plan is silent on errors, check that the implementation's choices are reasonable (don't return 200 OK with "error: true" in the body).
- **Data flow is correct end-to-end.** Trace the data from input to output for each requirement. Does the right data reach the right destination? Are transformations applied in the right order?

### Integration & Side Effects

- **Side effects match the spec.** If the plan says "sends an email", verify the email sending code exists and is triggered at the right point. If the plan says "logs an audit event", verify the audit log call exists.
- **New dependencies are justified.** Did the implementor pull in a new library? Is it necessary for the requirements, or was it a convenience choice?
- **Configuration and feature flags.** If the plan mentions toggling behavior via config or feature flags, verify the flag is wired correctly.

## What NOT to Flag

- Code quality issues — `code_quality` reviewer handles those
- Performance concerns — `performance` reviewer handles those
- Test quality — `tests` reviewer handles those
- Security issues — `security` reviewer handles those
- Pre-existing behavior in untouched code

## Judgement

Functional review is about fidelity to the spec. Two special cases:

- **The plan is wrong or incomplete.** If you find a genuine gap between what the plan says and what the codebase needs, flag it as a requirement gap rather than a scope creep — the implementor may have spotted something the plan missed. But favor the plan over assumptions.
- **The plan is ambiguous.** If a requirement can reasonably be interpreted multiple ways and the implementor picked one, note it but don't block on it. Flag it as "ambiguous requirement — human should clarify" rather than blocking.

## Output Format

Return JSON:

```json
{
  "approved": false,
  "blocking": [
    {
      "requirement": "Users can export their data as CSV",
      "file": "src/services/export.service.ts",
      "line": 1,
      "issue": "Missing Requirement",
      "detail": "The plan requires CSV export, but the implementation only handles JSON export. The `exportData` function outputs JSON with no CSV code path.",
      "suggestion": "Add a CSV export implementation or update the plan to specify JSON"
    },
    {
      "requirement": "Password reset sends email",
      "file": "src/handlers/auth.ts",
      "line": 89,
      "issue": "Incomplete Implementation",
      "detail": "The reset token is generated and stored, but no email is sent. The plan requires a confirmation email with the reset link.",
      "suggestion": "Add a call to the email service after token generation, passing the user's email and the reset link"
    },
    {
      "file": "src/routes/health.ts",
      "line": 1,
      "issue": "Scope Creep",
      "detail": "A /health endpoint was added that isn't mentioned in the plan. It's a new public API surface not asked for.",
      "suggestion": "Remove the health endpoint or justify why it's needed for this feature"
    },
    {
      "requirement": "Only admins can view audit logs",
      "file": "src/handlers/audit.ts",
      "line": 12,
      "issue": "Wrong Behavior",
      "detail": "The plan says only admins can access audit logs, but the handler checks for any authenticated user — no role check.",
      "suggestion": "Add a role check: if user.role !== 'admin', return 403 Forbidden"
    }
  ]
}
```

Or when clean:

```json
{
  "approved": true,
  "blocking": []
}
```

## Before Returning

- [ ] Every requirement in the plan was checked against the diff
- [ ] Every new endpoint, function, or behavior in the diff is traceable to the plan
- [ ] Every `blocking` item references the specific requirement from the plan
- [ ] Every `blocking` item has a file, line, issue category, detail, and suggestion
