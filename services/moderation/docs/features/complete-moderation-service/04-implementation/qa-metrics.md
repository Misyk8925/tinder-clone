# QA metrics: complete-moderation-service — working tree

**Scope:** moderation service full feature; excludes sibling-service integration and production deployment  
**Environment:** local  
**Reporting time:** 2026-09-06  
**Evidence index:** Gradle reports under `build/reports/tests/`

## 1. Acceptance traceability

| Requirement / contract error | Scenario | Status | Evidence / reason |
|---|---|---|---|
| FR-1/3/4/5/6/7 | synchronous moderation + focused unit/provider tests | Passed | REST slice and provider/context checks green. |
| Remaining FR/error rows | `CompleteModerationServiceAcceptanceTest` | Partial | Core REST, policy lifecycle, auth, GUI and metrics scenarios are implemented; rate-limit/Kafka-outbox and deterministic failure-injection evidence remain. |

**Traceability:** 34 / 34 requirements and contract error rows mapped; implementation evidence pending.

## 2. Test-run health

| Suite | Passed | Failed | Skipped | Blocked | Duration | Evidence / blocker |
|---|---:|---:|---:|---:|---:|---|
| Regression/component | — | 0 | 0 | 0 | — | `gradle test` green after persistence/review/GUI changes. |
| Acceptance | — | 5 known | 0 | 0 | — | Remaining rows are rate-limit, Kafka/outbox, and failure-fixture semantics; compile and application smoke are green. |
| Contract | 31 structural surfaces | 0 | 0 | 0 | <1 s | 19 HTTP + 5 event + 7 table checks. |
| Live provider | 0 | 0 | 0 | 1 | — | API credentials not supplied. |

**Repeat failures / suspected flakes:** none; red acceptance failures are deterministic missing behaviour.

### Mutation testing

N/A until changed policy/security state-transition logic exists; provider I/O wrappers are not useful mutation targets.

## 3. Risk-based verification

| NFR / risk | Approved target | Test or probe | Environment | Result | Evidence / blocker |
|---|---|---|---|---|---|
| NFR-1 | p95 <= 2 s at 50 RPS | load test | local | Blocked | runnable service not implemented yet |
| NFR-2 | provider timeout 1500 ms, then HOLD | failure injection | local | Failed | implementation pending |
| NFR-3 | 20 messages / 16 KiB | boundary tests | local | Passed | preprocessor boundary tests. |
| NFR-5 | one decision/event for 20 duplicates | PostgreSQL concurrency test | local | Passed (decision) | `JdbcPersistenceIntegrationTest`; event half awaits outbox/Kafka slice. |
| NFR-7 | no sensitive log values | captured-log test | local | Failed | implementation pending |

## 4. Confirmed defects and regression protection

| Source | Blocker | Major | Minor | Cosmetic | Evidence / issue links |
|---|---:|---:|---:|---:|---|
| Before release | 0 | 0 | 0 | 0 | targeted review not started |
| Escaped | 0 | 0 | 0 | 0 | not released |

## 5. Release outcome

Not ready for release; Kafka/outbox, production failure injection, rate limiting and live provider smoke remain.
