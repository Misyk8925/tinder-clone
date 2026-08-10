# Phase 2 — API & event contracts

Goal: turn the prose from phase 1 into something a machine can validate against **and** something a human can review in thirty seconds without opening a YAML file.

Only start this after phase 1 is approved.

## The rule: one canonical contract, one readable view

First locate the project's canonical spec, migration, and schema-documentation locations. Update them in place and link them from the feature index; never copy them into a workflow folder. For every connection type the feature actually uses, make both machine validation and human review possible:

| Connection type | Canonical (machine) | Mirror (human/agent) |
|---|---|---|
| HTTP API | project's canonical HTTP spec | generated docs or maintained readable view |
| Events (queue/broker) | project's canonical event schema/spec | generated docs or maintained readable view |
| Websockets | project's canonical channel/message spec | generated docs or maintained readable view |
| Database schema change | real migration file(s) | project data catalog or schema documentation |

Neither capability is optional, but they need not be two copied files:

- The **canonical spec or migration** is what tooling validates or executes. It remains the source of truth.
- The **readable view** may be generated documentation, an existing portal, or a maintained mirror. If maintained separately, it must cover the full changed surface and be checked for drift.

Do not create empty contract folders for unaffected connection types.

## One spec format per connection type, for the whole project

The choice of spec format is **not a per-feature decision** — it is a project-wide convention, made once, so that every HTTP endpoint in the codebase is described the same way and tooling (linting, mock servers, client generation) only has to support one format per type.

- If the project already has a format, use it.
- With no established format, default HTTP to OpenAPI 3.1 and events/websockets to AsyncAPI.

Before writing a new spec, check repository instructions, existing specs, generated docs, and linter configuration. If this is the first contract of its type, record the selected canonical location, format, and validation command in the project profile or contract conventions.

## Keeping the pair in sync

Write the canonical spec first. Generate the readable view when tooling exists. If it is maintained by hand, check it against the changed canonical surface before approval; drift is worse than having only the source of truth.

## What goes in the HTTP pair

For every endpoint, the canonical spec and readable view together cover:

- Method, path, and which FR it serves.
- Auth: who may call it, which scope/role.
- Request: full schema, required vs optional, validation rules, size limits.
- Response 2xx: full schema, with a realistic example.
- **Every error**: status code, error code, when it happens, whether the client should retry.
- Idempotency: is a repeated call safe? If it creates something, how is a duplicate detected?
- Pagination, filtering, sorting — if a list is returned, decide now.

The error table is the part that gets skipped and the part that gets you. A happy-path-only contract is a wish.

```
| Status | Code                | When                          | Retry? |
|--------|---------------------|--------------------------------|--------|
| 400    | INVALID_PAYLOAD     | Schema validation failed       | No     |
| 401    | UNAUTHENTICATED     | Missing/expired token          | No     |
| 409    | QUOTE_ALREADY_SENT  | Quote is in state SENT         | No     |
| 429    | RATE_LIMITED        | > 60 req/min per tenant        | Yes, after Retry-After |
| 503    | LLM_UNAVAILABLE     | Upstream model timeout         | Yes, backoff |
```

`openapi.yaml` holds this as `responses` per status code; `openapi.md` holds it as a readable table like the one above, plus prose for anything a schema can't express (business meaning of a field, when to use one error code vs another that looks similar).

## What goes in the events / websockets pair

For every event or message: name, topic/queue/channel, producer, consumers, partition/routing key, payload schema, versioning rule, delivery semantics (at-least-once? then consumers must be idempotent — say how), ordering guarantees, retention, and what goes to the dead-letter queue. Websockets additionally need: connection lifecycle (auth on connect, heartbeat, reconnect behaviour), and which messages are server→client vs client→server.

`asyncapi.yaml` encodes channels, messages, and bindings. `asyncapi.md` is the same content as prose and tables, plus the reasoning a spec file can't hold — why this partition key, why at-least-once instead of exactly-once, what a consumer must do to be idempotent.

## What goes in the data pair

The **migration** is the canonical schema change — real SQL (or the project's migration tool format), applied the normal way, reviewed the normal way. It is not duplicated into a markdown file; markdown does not run.

`data-catalog.md` documents what the migration doesn't and can't say cleanly:

- Per table: purpose, owner, and where the tenant boundary is enforced (row-level, schema, database) — this is a decision, not a detail.
- Per column that isn't self-explanatory: meaning, units, whether it's PII, retention/deletion rule.
- Relationships that matter for reasoning about the data but aren't just a foreign key (e.g. "a quote's `total` is denormalized from its line items and must be recalculated on any line-item change").
- Indexes added and the query they exist for — an index with no known query is a guess, not a decision.

If the feature adds no new tables or columns, this pair doesn't exist — but check: adding a column to an existing table still needs a catalog update for that column.

## Cross-cutting (once in the canonical contract documentation or feature contract index)

- Auth/authz model and where it is enforced.
- Versioning strategy for the API.
- Rate limits and quotas.
- What is logged, what is traced, what must never be logged.

## Inputs to the combined check after phase 3

Do not run or present these as a separate phase-2 review or gate. Carry them into the combined phase-2/3 verification after executable behaviour exists:

- Every FR maps to at least one endpoint, event, or schema change. Everything in the contracts maps back to an FR — if it doesn't, either the FR list or the contract is wrong.
- Every affected boundary has a canonical artifact and a readable review path.
- Any separately maintained readable view matches the changed canonical surface.
- Every NFR that the contract can violate is addressed (page sizes, timeouts, limits).
- Examples are realistic, not `"foo": "bar"`.
- Breaking-change policy is stated.
- Anything found while writing the contracts that is not resolved becomes an owned risk in the selected tracker, not an invented contract promise.

Continue to phase 3 without requesting approval. Validate the requirements, technical contracts, and executable behavioural contracts as one package there. The single design approval happens after that combined check, because a contract is easier to judge when the user can see the behaviour that will verify it.
