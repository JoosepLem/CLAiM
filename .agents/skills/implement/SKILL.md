---
name: implement
description: "Implement a piece of work based on a plan, with context from context_builder and iterative feedback from reviewers."
disable-model-invocation: true
---

Implement the work described in the plan. You receive the plan and optionally a list of blocking feedback from the previous review round. The reviewers (code_quality, performance, tests, security, functional) will check your output against the same dimensions you commit to below.

## Inputs

- **plan** — the feature spec or PRD to implement
- **feedback** — list of blocking issues from the previous review round (empty on first pass); fix these first

## Process

1. **Read the plan and feedback.** Understand what needs to be built.
2. **Explore the codebase briefly.** Identify relevant files, conventions, patterns — don't spawn sub-agents, just read directly.
3. **Implement everything in one pass.** Write all new files and modify all existing files needed. Don't stop to write individual unit tests for each class — the plan's e2e/integration tests are sufficient. Don't run `./gradlew test` after each class.
4. **Compile once, then fix everything.** Run `./gradlew compileKotlin compileTestKotlin`. Fix ALL compilation errors — both yours and any pre-existing ones that block the build. The project must compile fully before running tests. Then run `./gradlew test` once for the full suite.
5. **If tests fail, fix and re-run the full suite** — don't run individual test classes.
6. **Don't add test dependencies** (`spring-security-test`, etc.) unless absolutely required. Prefer simpler test approaches that use what's already available.

## Quality Bars

Each dimension below maps to a reviewer that will check your output. Write code that passes all five.

### 1. Code Quality

- **Names reveal intent.** Functions, variables, types, and files are named after what they do, not how. No `data`, `info`, `handle`, `process`, `tmp`, `Manager`, `Util`.
- **No duplicated logic.** If the same shape appears more than once, extract it into a shared function once the duplication is clear.
- **Methods stay near their data.** A method that reaches into another object's internals more than its own should live on that other object.
- **Data clumps become types.** Repeated parameter groups (e.g. `(host, port, timeout)` everywhere) get bundled into a single value type.
- **Primitives aren't domain concepts.** Strings and numbers that represent real things (`Email`, `Currency`, `Percentage`) get their own small types.
- **No speculative generality.** Don't add abstraction, parameters, hooks, or configuration points unless the plan explicitly asks for them. Build concrete first, abstract only when a second caller arrives.
- **Modules change for one reason.** Don't spread one logical change across many files, and don't make one file the landing zone for unrelated changes.
- **No middle men.** Don't create classes or functions that just delegate to another without adding value. Cut them and call the real target directly.
- **Strategy over inheritance.** Prefer composition. If a subclass ignores or overrides most of what it inherits, drop the inheritance.
- **No message chains.** Hide long navigation chains (`a.b().c().d()`) behind a single method on the first object.

### 2. Performance

- **No N+1 queries.** Loop-in-query patterns are the most common performance bug. Batch, join, or preload instead.
- **Optimize SQL queries.** Every new or modified query should use appropriate WHERE clauses to limit result sets, SELECT only needed columns (no `SELECT *`), and join efficiently. Add database indexes to support new query patterns — include the migration file so the reviewer can verify.
- **Add index migrations where necessary.** When introducing a new query that filters or joins on a column, check whether an index exists. If not, add a migration that creates the appropriate index. Compound indexes for multi-column filters, partial indexes for filtered subsets.
- **Minimize allocations in hot paths.** Reuse buffers, avoid boxing primitives, don't allocate inside tight loops when the allocation can move outside.
- **Pick appropriate data structures.** Use sets for membership checks, maps for lookups, not arrays. Don't sort when you only need a min/max.
- **Lazy where cheap to be lazy.** Defer expensive work until it's actually needed, but don't add lazy wrappers that cost more than the work they defer.
- **No premature optimization.** Write clear code first. Only optimize a path when you have evidence (profiling, benchmarking) that it's a bottleneck.

### 3. Tests

- **Test at seams.** Every test exercises behavior through a public interface. No tests against private methods or internal state.
- **No tautological tests.** Expected values come from an independent source of truth — a known-good literal, a worked example, the spec. Never recompute the expected value the same way the code does.
- **Cover the plan's acceptance criteria.** Each requirement in the plan should have at least one test that demonstrates it works.
- **Edge cases have tests.** Known edge cases (empty inputs, boundary values, error paths) get dedicated tests.
- **Don't write per-class unit tests** unless the plan explicitly asks for them. E2e and integration tests through the public HTTP interface are preferred.

### 4. Security

- **No secrets in code.** Never hardcode passwords, API keys, tokens, or connection strings. Read them from environment variables or a secrets manager.
- **Validate all inputs at the boundary.** Every input from outside the module — user input, API payloads, file contents, query parameters — gets validated before processing.
- **Prevent injection.** Use parameterized queries for databases, escape output for the target context (HTML, shell, SQL), never concatenate user input into commands or queries.
- **No unsafe deserialization.** Don't deserialize untrusted data into objects that can execute code (`pickle`, `eval`, `yaml.load` without SafeLoader). Use safe parsers.
- **Respect tenant boundaries.** Every data access must be scoped to the current tenant. Queries must include a tenant filter. Never leak data across tenants — a user in tenant A must never see or modify data belonging to tenant B, even if they guess an ID.
- **Enforce role boundaries.** Authorization checks happen at every entry point, not just the UI. A user with a limited role must not be able to call an endpoint or execute a code path intended for admins, even if they craft the request manually. Validate roles server-side on every protected action.
- **Principle of least privilege.** Code runs with the minimum permissions it needs. Don't request more access than the feature requires.

### 5. Functional Correctness

- **Implement exactly what the plan asks for.** Match each requirement in the plan to implemented behavior. If the plan says "users can reset their password", the flow should include email, token, expiry, confirmation.
- **No scope creep.** Don't add features, endpoints, or behaviors the plan doesn't mention. If you spot a gap, note it in a comment — don't fill it silently.
- **Handle error states the plan describes.** If the plan calls out specific error responses or failure modes, implement them. If the plan is silent on errors, choose reasonable defaults and document the choice.
- **Behavior matches the spec, not your assumptions.** When the plan is ambiguous, flag it rather than guessing. A wrong assumption is worse than a known open question.

## Delegating Back to Human

If you encounter ambiguity in the plan that you cannot resolve on your own, stop and delegate back. Don't guess — a wrong assumption is worse than a known open question.

**When to delegate:**
- The plan is silent on a critical behavior and reasonable people would disagree on the answer.
- Two requirements in the plan contradict each other.
- The plan references a concept, API, or system you can't find in the codebase and can't infer.
- The plan assumes a data model or schema that doesn't exist and can't be reasonably derived.

**When NOT to delegate:**
- The plan leaves minor details open (error messages, log levels, internal variable names). Choose reasonable defaults.
- You're unsure about the best implementation approach but all approaches satisfy the plan. Pick the simplest one.

**How to delegate:**
Return JSON with `"status": "delegated_to_human"` and a `"reason"` field that describes exactly what's ambiguous and what options you see:

```json
{
  "status": "delegated_to_human",
  "reason": "The plan says 'users can export their data' but doesn't specify which format. Options: CSV, JSON, PDF. Which one?"
}
```

The pipeline will exit and surface this to the human. After the plan is clarified, the pipeline will run again with the updated plan.

## Addressing Feedback

When feedback is provided (iteration > 1), treat blocking issues as the highest priority:

1. Read each blocking issue and understand what the reviewer found.
2. Fix the issue in the code — don't argue, don't work around it.
3. Add a regression test if the issue reveals a test gap.
4. Don't introduce new issues while fixing old ones. Keep the diff focused.

## Output Format

Return JSON. On success, include the diff and a summary:

```json
{
  "status": "diff",
  "diff": "<the full diff>",
  "summary": "Implemented password reset flow: email token generation, token validation endpoint, password update with bcrypt hashing, and expiry handling."
}
```

If you hit unsolvable ambiguity, return a delegation request instead (see Delegating Back to Human above).

## Before Returning the Diff

- [ ] All new tests pass
- [ ] No existing tests broken
- [ ] Typechecking passes
- [ ] Linting passes (or only pre-existing warnings)
- [ ] Each quality bar above was considered
- [ ] No leftover debug logs, commented-out code, or TODO stubs
- [ ] Review the diff yourself — if you spot something the reviewers will flag, fix it now
