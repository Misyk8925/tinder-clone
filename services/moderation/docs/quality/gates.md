# Deterministic quality gates: moderation service

**Owner:** moderation service  
**Baseline:** current working tree before full-service implementation  
**Last reviewed:** 2026-09-06

## Blocking gates

| Gate | Tool and command | Threshold | Latest result |
|---|---|---|---|
| API / AsyncAPI consistency | `python3 scripts/validate_contracts.py` | no unresolved ref/readable drift | Passed |
| Regression | `./gradlew test` | zero failures | Passed |
| Acceptance | `./gradlew acceptanceTest` | zero failures before Phase 5 | Failed as expected pre-implementation |
| Migration safety | Testcontainers PostgreSQL + Flyway | empty schema and restart pass | Pending slice 2 |
| Config/secrets | focused tests and source scan | no committed secrets/default production password | Pending |
| Dependency vulnerability/license | resolved dependency report | no known critical/high unresolved; licences reviewed | Pending |
| Performance | reproducible local load probe | NFR-1 | Pending slice 5 |

## Migration evidence

| Migration | Empty schema | Current-app compatibility | Expand/contract plan | Evidence |
|---|---|---|---|---|
| V1 | Pending | N/A, initial service schema | additive initial schema | slice 2 Testcontainers test |

## Performance evidence

| Operation | NFR | Baseline | Current result | Workload |
|---|---|---|---|---|
| `POST /internal/v1/moderations` | p95 <= 2 s | none | Pending | 50 RPS with 1 s provider stub |
