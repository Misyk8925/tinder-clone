# Phase 2 — API & event contracts

Goal: turn the prose from phase 1 into something a machine can validate against **and** something a human can review in thirty seconds without opening a YAML file.

Only start this after phase 1 is approved.

## The rule: always two files, never one

For every connection type the feature actually uses, produce a pair:

| Connection type | Canonical (machine) | Mirror (human/agent) |
|---|---|---|
| HTTP API | `02-contracts/http/openapi.yaml` | `02-contracts/http/openapi.md` |
| Events (queue/broker) | `02-contracts/events/asyncapi.yaml` | `02-contracts/events/asyncapi.md` |
| Websockets | `02-contracts/websockets/asyncapi.yaml` | `02-contracts/websockets/asyncapi.md` |
| Database schema change | real migration file(s) | `02-contracts/data/data-catalog.md` |

Neither file is optional and neither is a substitute for the other:

- The **spec** is what CI validates requests against, what generates client/server stubs, what a contract-testing tool (Pact, Spring Cloud Contract) checks. It is the source of truth when the two disagree.
- The **mirror** is what gets read during the approval gate, during phase-4 code review, and by an agent picking up the feature later without wanting to parse a 400-line YAML file. It must be a **full** mirror — every endpoint, every error, every event, not a summary that leaves things out. If the mirror is missing something the spec has, the mirror is wrong; fix it before asking for approval.

Only skip a pair if the feature genuinely has no connections of that type — a feature with no websocket channel gets no `websockets/` folder at all, not an empty one.

## One spec format per connection type, for the whole project

The choice of spec format is **not a per-feature decision** — it is a project-wide convention, made once, so that every HTTP endpoint in the codebase is described the same way and tooling (linting, mock servers, client generation) only has to support one format per type.

- HTTP → OpenAPI 3.1
- Events, websockets → AsyncAPI (it covers both; use the right `channels` binding for each)

Before writing a new spec, check whether the project already has a convention — a `docs/contracts/conventions.md`, an existing `openapi.yaml` elsewhere in the repo, a linter config (Spectral, etc.). If one exists, follow it. If this is the first feature in the project to need a given connection type, write the convention down (`docs/contracts/conventions.md` — project-level, not per-feature) so the next feature doesn't have to decide again.

## Keeping the pair in sync

Write the spec first — it is more constrained, so it forces you to actually decide the ambiguous bits (is this field nullable? what's the exact enum?). Then write the mirror from it, not the other way around. If the project has a spec-to-markdown generator (`widdershins`, `redoc-cli`, a custom AsyncAPI template), use it as a starting point and hand-edit for readability rather than writing the mirror from scratch. If no such tool is available, write it by hand but treat "does the mirror match the spec" as a checklist item before every approval request — drift between the two is worse than only having one file, because now there's a wrong answer as well as a right one.

## What goes in the HTTP pair

For every endpoint, both files need:

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

## Cross-cutting (once, at the top of `02-contracts/`, not duplicated per connection type)

- Auth/authz model and where it is enforced.
- Versioning strategy for the API.
- Rate limits and quotas.
- What is logged, what is traced, what must never be logged.

## Checks before the gate

- Every FR maps to at least one endpoint, event, or schema change. Everything in the contracts maps back to an FR — if it doesn't, either the FR list or the contract is wrong.
- Every pair that should exist, exists — both files, not one.
- The mirror actually matches the spec (spot-check a few endpoints/events after writing both).
- Every NFR that the contract can violate is addressed (page sizes, timeouts, limits).
- Examples are realistic, not `"foo": "bar"`.
- Breaking-change policy is stated.
- Anything found while writing the contracts that isn't fixable here and now — an external system's undocumented rate limit, an unclear ownership boundary, a dependency whose SLA is unknown — becomes a Linear issue labeled `risk`/`risk-open`, not silently absorbed into the spec as if it were settled.

Then stop and ask for approval. Once given, close the phase-2 Gate issue in Linear with a comment recording it, and post a Project Update summarizing what was approved.
