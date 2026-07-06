---
name: context_builder
description: "Build a structured codebase context object from a plan. Explores the repo to find relevant files, conventions, patterns, and architecture so the implementor and reviewers have the lay of the land without searching from scratch."
disable-model-invocation: true
---

Given a plan (feature spec / PRD), produce a structured context object that the implementor and reviewers consume. The goal is to front-load all codebase exploration so downstream agents don't each spend time searching.

## Input

- **plan** — the feature spec string

## Process

### 1. Read the plan

Extract the nouns and verbs. What domain concepts does it mention? What existing modules, endpoints, or data models does it touch? What new things does it introduce?

### 2. Map the plan to the codebase

For each concept in the plan, find where it lives (or where it should live):

- **Existing modules** — search for files, directories, and packages that match the domain terms. Read their public interfaces, not their internals.
- **Entry points** — find the routes, controllers, CLI commands, or event handlers the feature connects to.
- **Data model** — find the relevant database migrations, schema files, ORM models, or type definitions.
- **Configuration** — find any config files, feature flags, or environment variables the feature might touch.

### 3. Discover conventions

Read the surrounding code to extract the repo's conventions:

- **File structure** — how are modules organized? Flat files, nested directories, feature folders?
- **Naming** — PascalCase, camelCase, snake_case? Prefix conventions (I-prefix, E-prefix)? File naming (index.ts, module-name.ts)?
- **Error handling** — result types, exceptions, error codes, or something else?
- **Dependency injection** — constructor injection, import-time singletons, service locator, or none?
- **Testing** — framework used, test file location, test naming, setup/teardown patterns.
- **Logging** — logger library, log levels, structured or freeform?

### 4. Gather architecture context

- **Relevant ADRs** — look in `docs/adr/`, `docs/architecture/`, `.agents/docs/` for decisions that apply.
- **Domain glossary** — if `UBIQUITOUS_LANGUAGE.md` or a domain model doc exists, extract the relevant terms.
- **Existing patterns** — does the repo use repository pattern, CQRS, event sourcing, MVC, layered architecture? Note it.

### 5. Identify gaps

Flag anything the plan requires that doesn't have an obvious home in the codebase yet. These become questions for the implementor (and potentially delegation triggers).

## Output Format

Return JSON with the following structure:

```json
{
  "feature": "short name of the feature",
  "domain_concepts": ["list", "of", "key", "terms"],
  "relevant_files": [
    {
      "path": "src/foo/bar.ts",
      "role": "the module this feature extends",
      "public_api": ["exportedFunction1", "SomeClass"]
    }
  ],
  "entry_points": [
    {
      "path": "src/routes/users.ts",
      "method": "POST",
      "route": "/api/users/:id/reset-password"
    }
  ],
  "data_models": [
    {
      "path": "src/models/user.ts",
      "table": "users",
      "key_columns": ["id", "email", "tenant_id", "role"]
    }
  ],
  "conventions": {
    "file_structure": "feature folders under src/",
    "naming": "camelCase functions, PascalCase classes, snake_case tables",
    "error_handling": "Result<T, E> pattern from src/shared/result.ts",
    "di": "constructor injection via tsyringe",
    "testing": "vitest, tests/ dir mirrors src/, describe/it blocks",
    "logging": "pino, structured JSON, error/info/debug levels"
  },
  "architecture": {
    "pattern": "layered: controllers → services → repositories",
    "relevant_adrs": ["docs/adr/003-database-access.md", "docs/adr/007-auth-strategy.md"]
  },
  "dependencies_of_note": ["bcrypt for hashing", "nodemailer for email", "zod for validation"],
  "gaps": ["No email service module exists yet — will need to create one"],
  "test_patterns": [
    {
      "path": "src/services/user.service.test.ts",
      "framework": "vitest + @total-typescript/shoehorn",
      "seam": "UserService public methods"
    }
  ]
}
```

## Before Returning

- [ ] Every concept from the plan has either a `relevant_file`, `data_model`, or `gap` entry
- [ ] Conventions are extracted from actual code, not guessed
- [ ] Entry points are verified by reading route/module files, not inferred from naming
- [ ] Gaps are honest — only flag things the plan needs that genuinely don't exist
