# QA metrics: complete-moderation-service — working tree

**Scope:** moderation service full feature; excludes sibling-service integration and production deployment  
**Environment:** local  
**Reporting time:** 2026-09-15  
**Evidence index:** Gradle reports under `build/reports/tests/`

## 1. Acceptance traceability

| Requirement / contract error | Scenario | Status | Evidence / reason |
|---|---|---|---|
| FR-1–19 | acceptance plus focused unit/component tests | Passed locally | 23/23 acceptance; async semantics are additionally covered by `ModerationMessagingTest`. |
| HTTP/event error rows | acceptance + failure injection | Passed locally | Includes 429, durable-store 503, provider HOLD, retry/DLQ and schema rejection. |

**Traceability:** 34 / 34 requirements and contract error rows mapped; live provider/broker evidence is separate and blocked.

## 2. Test-run health

| Suite | Passed | Failed | Skipped | Blocked | Duration | Evidence / blocker |
|---|---:|---:|---:|---:|---:|---|
| Regression/component | 87 | 0 | 5 | 5 infrastructure-dependent | local clean run | `./gradlew clean test`; skipped rows require Docker/Testcontainers. |
| Acceptance | 23 | 0 | 0 | 0 | 6.923 s | `./gradlew acceptanceTest`. |
| Contract | 31 structural surfaces | 0 | 0 | 0 | <1 s | 19 HTTP + 5 event + 7 table checks. |
| Keyless runtime/browser | health + 2 decisions + 5 pages | 0 | 0 | 0 | local | clean and keyword-matched text safely `HOLD`; 400 px admin smoke passed. |
| Live provider/broker/database | 0 | 0 | 0 | 3 | — | Credentials and Docker were not supplied; not counted as passed. |

**Repeat failures / suspected flakes:** none; red acceptance failures are deterministic missing behaviour.

### Mutation testing

N/A: the changed high-risk paths are I/O/transaction wiring; focused failure and
concurrency checks give more signal than mutation of wrappers.

## 3. Risk-based verification

| NFR / risk | Approved target | Test or probe | Environment | Result | Evidence / blocker |
|---|---|---|---|---|---|
| NFR-1 | p95 <= 2 s at 50 RPS | warm 1-second provider stub | local MockMvc/in-memory | Partial: latest 1072 ms | Two warmed paced HTTP/auth/serialization runs passed; PostgreSQL-backed run is blocked without Docker. |
| NFR-2 | provider timeout 1500 ms, then HOLD | delayed HTTP fixture + retrying failure | local | Passed | adapter stops before 2.3 s; use case retries then returns `HOLD`. |
| NFR-3 | 20 messages / 16 KiB | boundary tests | local | Passed | preprocessor boundary tests. |
| NFR-4 | request <= 1 MiB | HTTP filter + acceptance | local | Passed | oversized body returns stable 413 before providers. |
| NFR-5 | one decision/event for 20 duplicates | in-memory concurrency + PostgreSQL test | local | Partial | in-memory passed; PostgreSQL rerun blocked without Docker. |
| NFR-6 | state survives restart | PostgreSQL integration | prior local Docker run | Passed previously | `JdbcPersistenceIntegrationTest`; current rerun blocked. |
| NFR-7 | no sensitive log/DLQ values | captured log + poison payload | local | Passed | marker values absent. |
| NFR-8/9 | BCrypt/cookies; 5 failures/15 min | config/browser + injected clock/concurrency | local | Partial | In-memory passed; atomic PostgreSQL lockout proof is present but blocked without Docker. |
| NFR-10 | audit every policy/review mutation | store/API tests | local | Passed | Successful and rejected/unauthorized mutations assert actor/action/target/outcome. |
| NFR-11 | raw content purged after 90 days | repository + deterministic cleanup | local | Partial | deterministic test passed; PostgreSQL image-only case blocked without Docker. |
| NFR-12 | five-attempt retry/DLQ | error-handler config + DLQ tests | local | Partial | deterministic path passed; live broker retry blocked without Docker. |

## 4. Confirmed defects and regression protection

| Source | Blocker | Major | Minor | Cosmetic | Evidence / issue links |
|---|---:|---:|---:|---:|---|
| Before release | 2 | 7 | 0 | 0 | CSRF plus final fresh-context review; all fixed with tests. |
| Escaped | 0 | 0 | 0 | 0 | not released |

## 5. Release outcome

Phase 4 remains blocked by unavailable PostgreSQL/Kafka integration evidence. Production
deployment was not requested. Release also remains gated on approved retention, deployment
Kafka names/ACLs, provider credentials/models, and scanners.
