---
name: tests
description: "Review the test code in a diff — are tests at the right seams, are assertions meaningful and non-tautological, does the setup make sense. Returns approved/blocking with actionable feedback."
disable-model-invocation: true
---

Review the test code in the given diff. The `check_tests` step already verifies tests pass — your job is to verify they're correct and meaningful. A passing test suite with broken tests is worse than a failing one.

## Input

- **diff** — the full diff produced by the implementor

## What to Check

### 1. Test Coverage

- **Each agreed-upon seam has at least one test.** Cross-reference the plan's acceptance criteria and the seams identified during implementation. Every acceptance criterion should map to a test.
- **Edge cases have dedicated tests.** Empty inputs, boundary values, error paths, null/undefined — each known edge case should have a test that explicitly exercises it.
- **Error paths are tested.** If the implementation handles errors (invalid input, not found, permission denied), those paths need tests that assert the error behavior.

### 2. Assertion Quality

- **Assertions use hard-coded expected values, not computed ones.** The expected value in an assertion must come from an independent source of truth — a literal, a known-good fixture, the spec — never recomputed the same way the code does it.
  - Bad (tautological): `expect(calculateTax(100, 0.08)).toBe(100 * 0.08)` — recomputes the expected value
  - Good: `expect(calculateTax(100, 0.08)).toBe(8.0)` — hard-coded literal
  - Bad: `expect(formatDate(d)).toBe(formatDate(d))` — tests nothing
- **Tests are sensitive to implementation changes.** If you mentally change the implementation behavior, does the test fail? If the test would pass regardless of what the code does, it's a fake test. Every assertion's expected value should be independently verifiable as the correct answer.
- **Assertions test behavior, not internal state.** Tests should verify observable outcomes through the public interface — return values, side effects, emitted events — not internal variables, private fields, or intermediate state.

### 3. Test Design

- **Test setup is realistic.** Mocks and fixtures reflect real usage patterns. Creating a user with admin privileges to test a regular-user flow is misleading. Stubbing an external service to always succeed when testing the error path is wrong.
- **Setup doesn't duplicate implementation logic.** If the test setup copies the exact same logic as the code under test (same transforms, same calculations), neither can catch bugs in the other.
- **One logical assertion per test, or bundled sensibly.** Multiple assertions on the same logical outcome are fine (`expect(user.name).toBe("Bob"); expect(user.email).toBe("bob@test.com")`). Multiple assertions on unrelated behaviors in one test are a smell — split into separate tests.
- **No false positives from over-mocking.** If a test mocks every collaborator, it tests the mocks, not the integration. At least one test should exercise the real composition of the module.

### 4. Test Execution

- **Tests pass.** Confirm with `check_tests` output. If tests fail, flag the specific failures.
- **No skipped tests.** `.skip`, `.todo`, `xit`, `xdescribe` — skipped tests should not remain in the diff. Either implement them or remove them.
- **No flaky tests.** If a test uses randomness, timers, or external services without pinning them, flag the flakiness risk.

## What NOT to Flag

- Missing tests for pre-existing code outside the diff
- Style preferences in test code (naming, formatting) — those come from code_quality and format/lint checks
- Performance of tests (slow test suites) — separate concern
- Whether tests should be integration vs. unit — the seam was pre-agreed

## Output Format

Return JSON:

```json
{
  "approved": false,
  "blocking": [
    {
      "file": "src/services/user.service.test.ts",
      "line": 34,
      "issue": "Tautological Assertion",
      "detail": "Expected value `user.hashedPassword === user.hashedPassword` — the assertion compares a value to itself. The test will never fail.",
      "suggestion": "Assert against a known-good hash output: expect(hashed).toBe('$2b$10$...')"
    },
    {
      "file": "src/handlers/auth.test.ts",
      "line": 67,
      "issue": "Misleading Setup",
      "detail": "Test named 'rejects expired tokens' but the mock setup returns a valid token with future expiry. The setup doesn't match what the test claims to verify.",
      "suggestion": "Set the mock token expiry to a past date"
    },
    {
      "file": "src/handlers/auth.test.ts",
      "line": 89,
      "issue": "Unreliable Test",
      "detail": "Test uses `setTimeout` with a 1ms delay to wait for an async operation. Race condition — may pass or fail depending on timing.",
      "suggestion": "Use await or a flush-promises helper instead of setTimeout"
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

- [ ] Every test in the diff was reviewed for tautological assertions
- [ ] Every acceptance criterion from the plan maps to at least one test in the diff
- [ ] No skipped or placeholder tests remain
- [ ] Every `blocking` item has a file, line, issue category, detail, and suggestion
