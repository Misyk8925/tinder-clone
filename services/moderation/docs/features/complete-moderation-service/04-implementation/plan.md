# Implementation plan: complete-moderation-service

## Slices

| # | Slice / observable result | Turns green | Depends on | Mode | Status |
|---|---|---|---|---|---|
| 1 | Context-aware synchronous moderation through REST with OpenAI classifier and Gemini adjudication adapters | FR-1/3/4/5/6/7 | approved contracts | HITL | code/test green; live smoke blocked on keys |
| 2 | Durable idempotent decisions and versioned Policy API | FR-8/9/10/11/12 | migration | HITL | code/test green; PostgreSQL round-trip/restart/concurrency green |
| 3 | Review workflow, simple auth, audit, and internal admin GUI | FR-13/14/15/16/17 | slice 2 | HITL | code/test green; acceptance fixture coverage added |
| 4 | Kafka consumer, outbox, retries, DLQ, health, and metrics | FR-2/18/19 | slices 2–3 | HITL | complete; live broker check blocked without Docker |
| 5 | NFR, failure, migration, security, browser, and final combined evidence | all NFR/error rows | slices 1–4 | HITL | complete; production release remains gated |

## Completed slice 4

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

- Result, review, and policy events are inserted through the transactional persistence
  adapters. Publisher failure leaves a due outbox row for retry.
- Kafka's error handler performs four retries after the first delivery and publishes a
  hash-only DLQ event on the fifth failure. V1 rejects other schema versions.
- Readiness probes real PostgreSQL/Kafka dependencies when enabled and reports the
  in-memory/disabled modes explicitly.
- `ModerationMessagingTest` proves command deduplication, retry persistence, event
  payload privacy, schema rejection, and DLQ privacy without a broker or provider keys.

## Completed slice 5

- Provider exceptions retry twice and become durable `HOLD/PROVIDER_UNAVAILABLE`, never
  `ALLOW` or a client-validation error.
- The 1 MiB HTTP envelope, 1500 ms provider timeout, 90-day raw-content cleanup, BCrypt
  configuration, 5-attempt/15-minute lockout, secure session cookies, audit records, and
  sensitive-log policy have executable evidence.
- A 50-request concurrent probe with a 1-second provider stub enforces p95 <= 2 seconds.
- Browser smoke at 400 px verified login, dashboard, decisions, reviews, and policies.
- Blank OpenAI/Gemini keys use the local fallback: clean profile text returns `ALLOW`;
  the fixture hate phrase returns `BLOCK`.

## Phase ledger — full-feature-delivery / slices 4–5

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P4.1 Slice plan | Done | Observable async and final-evidence slices above; no migration added. |
| P4.2 Primary evidence/red | Done | Existing FR-2/18/19 and NFR rows were incomplete before this work. |
| P4.3 Implementation | Done | Kafka/outbox, failure handling, security, retention, readiness, and keyless fallback. |
| P4.4 Unit | Done | Provider HOLD, lockout, retention, health, cookie and messaging tests. |
| P4.4 Component | Done | 87 tests passed; 5 Testcontainers cases skipped because Docker is unavailable. |
| P4.4 Integration | Blocked | Live PostgreSQL/Kafka Testcontainers require Docker; prior PostgreSQL evidence remains recorded below. |
| P4.4 Contract | Done | `python3 scripts/validate_contracts.py`: 19 HTTP, 5 event, 7 table surfaces. |
| P4.4 System/e2e | Done | Keyless bootJar smoke plus browser smoke at 400 px. |
| P4.4 Specialist | Done | p95, timeout, PII logs/DLQ, retention, lockout, cookies, idempotency. |
| P4.5 Error paths | Done | Provider timeout, storage 503, poison/schema event, publish retry, dependency-down readiness. |
| P4.6 Fresh-context review | Done | Independent review found eight defects; all were fixed with regression evidence. |
| P4.7 Targeted defect review | Done | CSRF login plus eight final-review findings recorded in `log.md`; no open confirmed defect. |
| P4.8 Quality gates | Done | Build, acceptance, contracts and local performance passed; Docker checks are explicitly blocked. |
| P4.9 Handoff | N/A | Same implementation context completed both slices. |
| P4.10 Combined-diff review | Done | Fresh-context final review covered transactionality, Kafka, security, retention and keyless operation. |

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
