# Behaviour traceability — complete-moderation-service

Acceptance format: native JUnit 5 tests with Gherkin-like domain names and Spring MVC boundary probes.

Validation commands:

- `./gradlew test` — expected green regression suite.
- `./gradlew acceptanceTest` — expected red before Phase 4 implementation.
- `python3 scripts/validate_contracts.py` — canonical/readable contract consistency.

Last checked: 2026-09-06. The acceptance task is discovered, compiles, executes, and is
red with 22/22 scenarios failing because the approved HTTP, security, persistence, Kafka,
GUI, and management adapters do not exist yet. This is the intended pre-implementation
failure, not a missing test engine.

Test file: [`CompleteModerationServiceAcceptanceTest.kt`](../../../../src/test/kotlin/com/tinder/clone/moderation/acceptance/CompleteModerationServiceAcceptanceTest.kt)

## FR → executable acceptance check

| FR | Test name |
|---|---|
| FR-1 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence` |
| FR-2 | `FR-18 FR-2 async boundaries are available in the running application` |
| FR-3 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence`; oversized/invalid error tests |
| FR-4 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence` |
| FR-5 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence` |
| FR-6 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence` |
| FR-7 | `FR-1 FR-3 FR-4 FR-5 FR-6 FR-7 synchronous moderation preserves context and evidence` plus existing domain boundary tests |
| FR-8 | `FR-8 FR-9 duplicate request replays the durable decision` |
| FR-9 | `FR-8 FR-9 duplicate request replays the durable decision`; `ERR-IDEMPOTENCY-409 reused key with another body conflicts` |
| FR-10 | `FR-10 FR-11 policy draft publishes and activates without restart` |
| FR-11 | `FR-10 FR-11 policy draft publishes and activates without restart` |
| FR-12 | `FR-12 policy preview does not create a moderation decision` |
| FR-13 | `FR-13 FR-14 review queue resolves with optimistic locking` |
| FR-14 | `FR-13 FR-14 review queue resolves with optimistic locking` |
| FR-15 | `FR-15 FR-16 internal admin GUI renders decisions reviews and policies` |
| FR-16 | `FR-15 FR-16 internal admin GUI renders decisions reviews and policies` |
| FR-17 | `FR-17 anonymous API caller is rejected`; `ERR-HTTP-403 viewer cannot mutate policy` |
| FR-18 | `FR-18 FR-2 async boundaries are available in the running application` |
| FR-19 | `FR-19 health and metrics endpoints are exposed internally` |

## Contract error → executable acceptance check

| Error ID | Test name |
|---|---|
| ERR-HTTP-400 | `ERR-HTTP-400 invalid request returns stable error` |
| ERR-HTTP-401 | `FR-17 anonymous API caller is rejected` |
| ERR-HTTP-403 | `ERR-HTTP-403 viewer cannot mutate policy` |
| ERR-HTTP-404 | `ERR-HTTP-404 unknown decision is not found` |
| ERR-IDEMPOTENCY-409 | `ERR-IDEMPOTENCY-409 reused key with another body conflicts` |
| ERR-VERSION-409 | `ERR-VERSION-409 stale policy update conflicts` |
| ERR-POLICY-EXISTS-409 | `ERR-POLICY-EXISTS-409 duplicate policy version conflicts` |
| ERR-HTTP-413 | `ERR-HTTP-413 oversized request is rejected before providers` |
| ERR-POLICY-IMMUTABLE-422 | `ERR-POLICY and review transitions return stable conflict errors` |
| ERR-POLICY-INVALID-422 | `ERR-POLICY-INVALID-422 invalid draft cannot publish` |
| ERR-POLICY-NOT-PUBLISHED-422 | `ERR-POLICY-NOT-PUBLISHED-422 draft cannot activate` |
| ERR-NO-PREVIOUS-422 | `ERR-NO-PREVIOUS-422 activation without history cannot roll back` |
| ERR-REVIEW-RESOLVED-422 | `ERR-POLICY and review transitions return stable conflict errors` |
| ERR-HTTP-429 | `ERR-HTTP-429 rate limit includes retry information` |
| ERR-HTTP-503 | `ERR-HTTP-503 unavailable durable storage is retryable` |

## Contract and acceptance validation notes

- OpenAPI and AsyncAPI parse; every local `$ref` resolves.
- The validator confirms all 19 HTTP operations occur in the readable HTTP view.
- The validator confirms all five event channels occur in the readable event view.
- The validator confirms all seven migration tables occur in the data catalog.
- HTTP v1 and event v1 are new compatibility boundaries; no existing external contract is changed.
- The Phase 4 database failure fixture must replace the explicit red marker in the 503 scenario.
- Kafka end-to-end and PostgreSQL restart assertions will be attached to the same named acceptance cases with Testcontainers in Phase 4; the present checks establish the approved observable surface.
