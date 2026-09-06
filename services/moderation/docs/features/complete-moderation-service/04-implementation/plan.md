# Implementation plan: complete-moderation-service

## Slices

| # | Slice / observable result | Turns green | Depends on | Mode | Status |
|---|---|---|---|---|---|
| 1 | Context-aware synchronous moderation through REST with OpenAI classifier and Gemini adjudication adapters | FR-1/3/4/5/6/7 | approved contracts | HITL | code/test green; live smoke blocked on keys |
| 2 | Durable idempotent decisions and versioned Policy API | FR-8/9/10/11/12 | migration | HITL | code/test green; PostgreSQL round-trip/restart/concurrency green |
| 3 | Review workflow, simple auth, audit, and internal admin GUI | FR-13/14/15/16/17 | slice 2 | HITL | code/test green; acceptance fixture coverage added |
| 4 | Kafka consumer, outbox, retries, DLQ, health, and metrics | FR-2/18/19 | slices 2–3 | HITL | todo |
| 5 | NFR, failure, migration, security, browser, and final combined evidence | all NFR/error rows | slices 1–4 | HITL | todo |

## Current slice: 2

**Observable result**

- Repeated moderation requests return one durable decision.
- Policy drafts can be validated, published, activated, and rolled back without restarting the service.

**Mode**

- HITL: this slice activates the approved initial PostgreSQL migration and immutable policy lifecycle.

**Blocking dependencies**

- A local PostgreSQL/Testcontainers runtime is required for migration and restart proof.

**Files to touch**

- Persistence entities/repositories, transaction service, policy resolver, Policy API, idempotency integration, and tests.

**New modules/dependencies**

- Spring JDBC/JPA or a smaller JDBC adapter, Flyway, PostgreSQL driver, and Testcontainers PostgreSQL.

**Migrations**

- Execute `V1__moderation_service.sql` unchanged or revise the contract first if implementation proves a gap.

**Config / secrets**

- Database URL, username, and password from environment; no committed credentials.

**Primary evidence**

- FR-8/9/10/11/12 acceptance scenarios plus migration and concurrency integration tests.

**Changed risks and test levels**

- Lost/duplicated decisions → database integration and 20-way concurrency tests.
- Mutable published policy → state-transition unit and database tests.
- Migration incompatibility → empty-schema Testcontainers gate.

**Manual check**

- Confirm database migration evidence before proceeding to GUI/review work.

**Biggest risk**

- A race between idempotency lookup and insert creating inconsistent responses.

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
