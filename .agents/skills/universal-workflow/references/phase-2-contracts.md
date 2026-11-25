# Phase 2 — Interface contracts

Goal: turn the prose from phase 1 into something a machine can validate against **and** something a human can review in thirty seconds without opening a YAML file.

A contract is any boundary the feature crosses, on any part of the stack: backend APIs and data, but also UI screens, shared components, client state, configuration, background jobs, and model I/O. The test for whether something belongs here: phase 3 must be able to write an acceptance test against it. Visual design (layout, colour, spacing) is not a contract; it belongs to phase 1 or the project's design tool.

Only start this after phase 1 is approved.

Keep every P2 ledger row from `references/phase-ledger.md`. An unused connection type is `N/A` with a reason, not an omitted section. Do not print the phase-2 ledger separately; it appears once in the combined phase-3 gate packet.

## The rule: one canonical contract, one readable view

First locate the project's canonical spec, migration, and schema-documentation locations. Update them in place and link them from the feature index; never copy them into a workflow folder. For every connection type the feature actually uses, make both machine validation and human review possible:

| Ledger | Boundary | Canonical (machine) | Mirror (human/agent) |
|---|---|---|---|
| P2.1 | HTTP API | project's canonical HTTP spec | generated docs or maintained readable view |
| P2.2 | Events (queue/broker) | project's canonical event schema/spec | generated docs or maintained readable view |
| P2.3 | Websockets | project's canonical channel/message spec | generated docs or maintained readable view |
| P2.4 | Database schema change | real migration file(s) | project data catalog or schema documentation |
| P2.5 | UI screen / route | route config plus view-state spec, stories, or typed state model | screen-state table in the feature index |
| P2.6 | Shared component API | typed inputs/outputs in code | component docs or stories |
| P2.7 | Client state | store/service types | state table: shape, writers, lifetime |
| P2.8 | Config / feature flags | config schema or flag definition | flag table: default, owner, off-behaviour |
| P2.9 | Scheduled / background jobs | job definition (cron, trigger, handler signature) | job table: trigger, idempotency, failure handling |
| P2.10 | Model / ML I/O | input/output schema plus pinned model identifier | model table: version, dimensions, fallback |
| P2.11 | Shared code contracts (libraries/modules consumed by several services) | the shared types themselves | changelog entry plus consumer list |

Neither capability is optional, but they need not be two copied files:

- The **canonical spec or migration** is what tooling validates or executes. It remains the source of truth.
- The **readable view** may be generated documentation, an existing portal, or a maintained mirror. If maintained separately, it must cover the full changed surface and be checked for drift.

Do not create empty contract folders for unaffected boundaries. Keep the unused types on the ledger as `N/A` instead.

## One spec format per boundary type, for the whole project

The choice of spec format is **not a per-feature decision** — it is a project-wide convention, made once, so that every HTTP endpoint in the codebase is described the same way and tooling (linting, mock servers, client generation) only has to support one format per type.

- If the project already has a format, use it.
- With no established format, default HTTP to OpenAPI 3.1 and events/websockets to AsyncAPI. For the non-API boundaries, prefer the typed code itself (TypeScript interfaces, config schema classes, job signatures) as canonical, plus a short table in the feature index as the readable view. Do not invent a new spec language for them.

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

If the feature adds no new tables or columns, this pair doesn't exist and P2.4 is `N/A` with that reason — but check: adding a column to an existing table still needs a catalog update for that column.

## What goes in the UI screen / route contract

For every new or changed screen or route:

- Route path, guards (auth, role, feature flag), and which FR it serves.
- Inputs: route params, query params, and the data it loads (link the HTTP contract rows).
- **Every view state**: loading, empty, success, partial, and each error state.
- User actions: what each one triggers (API call, navigation, state change) and what the screen shows while it is pending.
- Accessibility commitments that a test can check (focus on open, labelled controls, keyboard path for the primary action).

The state table is the UI equivalent of the HTTP error table:

```
| State          | Trigger                        | Shows                          | Next action      |
|----------------|--------------------------------|--------------------------------|------------------|
| loading        | deck request in flight         | skeleton cards                 | —                |
| empty          | 200 with no candidates         | "No one new nearby" + widen CTA| open filters     |
| rate-limited   | 429 RATE_LIMITED               | toast, swipe disabled          | auto-retry after Retry-After |
| offline        | network error                  | banner, cached deck            | retry button     |
```

TODO(human): define the rule that ties UI error states to the HTTP error table.

## What goes in the other non-API contracts

- **Shared component API**: inputs (required vs optional, defaults), outputs/events, slots/content projection, and which states the component renders itself vs leaves to its parent.
- **Client state**: shape, which code may write it, lifetime (page, session, persisted), and what clears it (logout, account switch).
- **Config / feature flags**: name, type, default, owner, where it is read, behaviour when off, and the removal condition for temporary flags.
- **Scheduled / background jobs**: trigger, input, idempotency key, concurrency (can two runs overlap?), retry and dead-letter behaviour, and what it emits.
- **Model / ML I/O**: input and output schema, pinned model identifier and dimensions, timeout, fallback when the model is unavailable, and how a model change is rolled out without mixing incompatible outputs.
- **Shared code contracts**: the changed types, every consumer that compiles against them, whether the change is source- or binary-breaking, and the order consumers are rebuilt in.

## Consumers on every boundary

For every changed boundary, list its producer and every consumer across the stack (services, gateway, clients, jobs). A contract without its consumer list cannot be judged for breakage. Breaking changes name the migration path for each consumer.

## Cross-cutting (once in the canonical contract documentation or feature contract index)

- Auth/authz model and where it is enforced.
- Versioning strategy for the API.
- Rate limits and quotas.
- What is logged, what is traced, what must never be logged.

## Inputs to the combined check after phase 3

Do not run or present these as a separate phase-2 review or gate. Carry them into the combined phase-2/3 verification after executable behaviour exists:

- Every FR maps to at least one contract row on some boundary (endpoint, event, schema change, screen state, flag, job, and so on). Everything in the contracts maps back to an FR — if it doesn't, either the FR list or the contract is wrong.
- Every affected boundary has a canonical artifact, a readable review path, and a consumer list.
- Every UI error state traces to a backend error or client failure, and every user-facing backend error has a UI state.
- Any separately maintained readable view matches the changed canonical surface.
- Every NFR that the contract can violate is addressed (page sizes, timeouts, limits).
- Examples are realistic, not `"foo": "bar"`.
- Breaking-change policy is stated.
- Anything found while writing the contracts that is not resolved becomes an owned risk in the selected tracker, not an invented contract promise.

Continue to phase 3 without requesting approval. Run the phase-2 exit review from `references/phase-exit-review.md` silently in that same turn; report only a finding or owner decision. Stop only if an owner decision appeared. Validate the requirements, technical contracts, and executable behavioural contracts as one package in phase 3. The single design approval happens after that combined check, because a contract is easier to judge when the user can see the behaviour that will verify it.
