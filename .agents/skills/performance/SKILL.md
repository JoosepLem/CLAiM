---
name: performance
description: "Review a diff for performance issues — N+1 queries, missing indexes, hot-path allocations, poor data structures. Returns approved/blocking with actionable feedback."
disable-model-invocation: true
---

Review the given diff for performance issues. You are checking whether the implementor followed the performance bars. Only flag issues that are likely to cause measurable slowdowns under real load — don't flag theoretical micro-optimizations.

## Input

- **diff** — the full diff produced by the implementor

## What to Check

### Database Queries

- **N+1 queries.** The most common and expensive performance bug. Look for queries inside loops. If a loop body executes a query that could have been batched, joined, or preloaded, flag it. Example: fetching a user's orders by calling `SELECT * FROM orders WHERE user_id = ?` inside a `for user in users` loop — should be a single `SELECT * FROM orders WHERE user_id IN (...)`.
- **Missing WHERE clauses.** Queries that load entire tables when only a subset is needed. `SELECT * FROM logs` in a request handler that only needs today's logs. Flag any query that lacks filtering when the caller's context implies a narrower result set.
- **SELECT * on wide tables.** Queries that select all columns when the caller only uses a few. Flag if the table has many columns or large text/blob columns and the code touches only a handful.
- **Inefficient joins.** Joins without indexes on the join columns, cross joins that should be inner/left joins, or joins that can be replaced with a subquery or EXISTS that would be faster.
- **Missing indexes.** Any query introduced or modified in the diff that filters, joins, or sorts on a column that lacks an index. Check whether a corresponding migration file was included. Flag if a new query pattern has no index support.
- **Missing index migrations.** If the diff includes a new query that needs an index but no migration file adds one, flag it. The implementor should have included a migration.

### Hot Paths

- **Allocations inside loops.** Objects, slices, or buffers allocated inside a loop body when they could be allocated once outside and reused. Flag heap allocations in tight loops.
- **Work in the wrong layer.** Expensive computation happening per-request that could be done once at startup. Heavy initialization in middleware vs. in a module init.
- **Unnecessary serialization.** Parse → modify → serialize cycles when the data could stay in its native format. JSON.parse → JSON.stringify round-trips.

### Data Structures

- **Wrong data structure for the access pattern.** Using an array for membership checks (`items.includes(x)` in a loop), using an unsorted array when sorted would enable binary search, linear scans of large collections when a Map or Set would give O(1) lookup.
- **Unnecessary sorting.** Sorting when the caller only needs a min, max, or top-N. Sorting the same collection repeatedly.
- **Large in-memory collections.** Loading an unbounded dataset into memory. Fetching all results when pagination or streaming would suffice.

### Lazy vs. Eager

- **Eager work that could be lazy.** Expensive computation or I/O done upfront when the result might never be used. Heavy initialization in a module that may not be imported.
- **Lazy wrapping that costs more than the work.** Adding a lazy wrapper (thunk, promise, generator) around something cheap where the wrapper overhead exceeds the deferred work.

## What NOT to Flag

- Theoretical micro-optimizations (saving a single allocation outside a hot path)
- Pre-existing performance issues in code unchanged by the diff
- Formatting, lint, test, security, or functional issues — those have their own reviewers
- O(n) algorithms where n is inherently bounded and small
- "Could be faster if we rewrote in Rust/WASM/C" — stay in the language the code is written in

## Judgement

Performance reviews weigh cost vs. impact:
- **Always flag:** N+1 queries, missing indexes on new queries, SELECT * on wide tables, O(n²) on unbounded n
- **Flag if hot path:** Allocations in request handlers, wrong data structures in frequently-called code
- **Skip:** Micro-optimizations, off-hot-path allocations, pre-existing issues

## Output Format

Return JSON:

```json
{
  "approved": false,
  "blocking": [
    {
      "file": "src/services/order.service.ts",
      "line": 87,
      "issue": "N+1 Query",
      "detail": "The `enrichOrders` function queries `order_items` once per order inside a loop over all orders. For 100 orders this produces 101 queries instead of 1.",
      "suggestion": "Batch-load order_items with a single query: SELECT * FROM order_items WHERE order_id IN (...)"
    },
    {
      "file": "src/db/queries/reports.ts",
      "line": 23,
      "issue": "Missing Index Migration",
      "detail": "New query filters on `reports.created_at` for date-range lookups but no index exists on that column and no migration was added.",
      "suggestion": "Add a migration: CREATE INDEX idx_reports_created_at ON reports(created_at)"
    },
    {
      "file": "src/utils/parser.ts",
      "line": 56,
      "issue": "Wrong Data Structure",
      "detail": "`isAllowed` uses `blockedList.includes(domain)` for every request. For a blocked list of 10k domains, this is O(n) per request. Use a Set.",
      "suggestion": "Convert blockedList to a Set for O(1) lookups"
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

- [ ] Every `blocking` item references a specific file and line in the diff
- [ ] Every `blocking` item includes an actionable suggestion
- [ ] No items that belong to another reviewer's domain
- [ ] N+1 queries and missing indexes are always flagged — these are never acceptable
