# Implementation plan: complete-moderation-service

## Slices

| # | Slice / observable result | Turns green | Depends on | Mode | Status |
|---|---|---|---|---|---|
| 1 | Context-aware synchronous moderation through REST with OpenAI classifier and Gemini adjudication adapters | FR-1/3/4/5/6/7 | approved contracts | HITL | code/test green; live smoke blocked on keys |
| 2 | Durable idempotent decisions and versioned Policy API | FR-8/9/10/11/12 | migration | HITL | code/test green; PostgreSQL round-trip/restart/concurrency green |
| 3 | Review workflow, simple auth, audit, and internal admin GUI | FR-13/14/15/16/17 | slice 2 | HITL | code/test green; acceptance fixture coverage added |
| 4 | Kafka consumer, outbox, retries, DLQ, health, and metrics | FR-2/18/19 | slices 2–3 | HITL | in progress |
| 5 | NFR, failure, migration, security, browser, and final combined evidence | all NFR/error rows | slices 1–4 | HITL | todo |

## Current slice: 4

**Observable result**

- A versioned command is consumed at-least-once, produces one durable decision, and a result event is written to the outbox.
- Five unsuccessful command attempts publish `ModerationCommandRejected` to the DLQ without raw content.
- Unpublished outbox rows survive a publisher retry/restart and are published exactly once logically.
- Readiness exposes `db` and `kafka`; `moderation.decisions` is registered before the first request.

**Mode**

- HITL: Kafka topic names stay configurable; live broker smoke is optional. Deterministic tests use in-process fakes so the slice can be proven without provider keys or Docker Kafka.

**Blocking dependencies**

- Slices 2–3 persistence/review/policy APIs.
- A real Kafka broker is not required for the primary evidence; Testcontainers Kafka remains optional if Docker is present.

**Files to touch**

- Outbox port/repository/publisher, command consumer and DLQ, Kafka listener error handler, health/metrics registration, policy/review outbox enqueue, tests.

**New modules/dependencies**

- None. Reuse Spring Kafka already on the classpath; do not add an embedded-Kafka module solely for CI without Docker.

**Migrations**

- None. Slice 4 uses the existing `moderation_outbox` table.

**Config / secrets**

- `moderation.kafka.enabled` remains false by default. No provider keys.

**Primary evidence**

- FR-2/18/19 acceptance plus command/outbox/DLQ unit tests and NFR-12 retry/DLQ coverage.

**Changed risks and test levels**

- Lost/duplicated result events → outbox unit + 20-way duplicate execution test.
- Poison commands → DLQ payload test (hash + diagnostic bound, no raw text).
- False-green health → readiness still lists kafka/db contributors.

**Manual check**

- Live Kafka broker and provider keys remain optional; report Blocked, not passed.

**Biggest risk**

- In-memory retry counts that reset on redelivery, or DLQ payloads that leak conversation text.

## Slice 2/3 evidence update

- `JdbcPersistenceIntegrationTest`: PostgreSQL 17 round-trip preserves normalized request,
  context and evidence; a new registry instance reloads policy and activation; 20 concurrent
  duplicate inserts resolve to one decision row.
- `ModerationMigrationTest`: Flyway executes V1 and V2 and creates all seven moderation tables.
- `ReviewController` and store implement OPEN → RESOLVED/ESCALATED with `If-Match`, terminal
  transition errors, and audit insertion.
- `AdminController` renders authenticated dashboard, decisions, review queue and policy pages;
  simple BCrypt users remain the only authentication mechanism.

## Completed evidence: slice 1

- Exact REST scenario `synchronous moderation preserves context and evidence`: GREEN.
- 59 regression/component tests: GREEN, including three provider adapter tests.
- Context over 16 KiB stops before classifier; Unicode context is normalized.
- OpenAI multimodal request and category mapping verified with local HTTP fixture.
- Gemini structured response and pseudonymized `SUBJECT/OTHER` context verified with local HTTP fixture.
- Self-review fixed late context validation, wrong missing-key `400`, malformed-body error mapping, content-too-large mapping, and JSON access-denied response.
- Live OpenAI/Gemini smoke: BLOCKED until keys are supplied.
