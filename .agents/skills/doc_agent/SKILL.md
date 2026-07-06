---
name: doc_agent
description: "Generate and update documentation from a completed implementation report. Produces changelog entries, ADRs, and updates domain/architecture docs as needed."
disable-model-invocation: true
---

Given the final pipeline report (all reviews approved, all checks passed), produce and commit documentation that captures what was built and why.

## Input

The report object:

```json
{
  "plan": "the feature spec / PRD",
  "iterations": 3,
  "reviews": [
    {"approved": true, "blocking": []},
    ...
  ],
  "checks": [true, true, true, true, true]
}
```

- **plan** — the original feature spec
- **iterations** — how many implement-review-loops it took
- **reviews** — the final review results from all reviewers
- **checks** — the pass/fail results from all check agents

## What to Produce

### 1. Changelog Entry

Write or update `CHANGELOG.md` (or equivalent) with an entry for this feature. Format:

```markdown
## [Unreleased]

### Added
- <one-line summary of the feature>. (<#issue>)

### Changed
- <any behavior changes introduced>
```

If no `CHANGELOG.md` exists, create one at the repo root.

### 2. Architecture Decision Record (if warranted)

If the implementation introduced a significant architectural decision — a new pattern, a new dependency, a new module boundary, a data model choice — create an ADR under `docs/adr/` or `.agents/docs/adr/`.

Use this template:

```markdown
# ADR-<NNN>: <Title>

**Date:** YYYY-MM-DD
**Status:** Accepted

## Context

<What was the situation that led to this decision? What problem were we solving?>

## Decision

<What did we decide to do? Be specific — which library, which pattern, which approach.>

## Consequences

<What are the trade-offs? What becomes easier? What becomes harder? Any follow-up work needed?>
```

Increment the ADR number from the last one in the directory. If no ADRs exist yet, start at `001`.

Only create an ADR if the decision is non-trivial. Adding a utility function or a new route handler does not warrant an ADR. Introducing a new state machine, a new integration pattern, or a cross-cutting concern does.

### 3. Domain Model Update (if applicable)

If the feature introduced new domain concepts or changed existing ones, update `UBIQUITOUS_LANGUAGE.md` or the project's domain glossary. Add new terms, update definitions, flag terms that changed meaning.

If no domain glossary exists but the feature introduced several new domain concepts, create one at the repo root or in `.agents/docs/`.

### 4. API / Interface Documentation (if applicable)

If the feature added new public API surface — endpoints, exported functions, CLI commands — check whether the repo has API docs and update them. If the repo uses inline doc comments (JSDoc, docstrings), note that the implementor should have added them and verify they exist in the diff.

## Process

1. Read the plan to extract the feature name and scope.
2. Read the reviews to extract any architecture decisions, domain model impacts, or API changes.
3. Read the existing docs directory to understand conventions.
4. Generate each applicable doc type.
5. Commit with a message like `docs: add changelog and ADR for <feature-name>`.

## Before Returning

- [ ] `CHANGELOG.md` updated with the feature entry under `[Unreleased]`
- [ ] ADR created if architectural decision was made (check reviews for patterns, new deps, new modules)
- [ ] Domain model updated if new concepts introduced
- [ ] All docs committed to the current branch
