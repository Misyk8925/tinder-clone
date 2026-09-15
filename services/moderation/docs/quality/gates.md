# Deterministic quality gates: moderation service

**Owner:** moderation service  
**Baseline:** current working tree before full-service implementation  
**Last reviewed:** 2026-09-15

## Blocking gates

| Gate | Tool and command | Threshold | Latest result |
|---|---|---|---|
| API / AsyncAPI consistency | `python3 scripts/validate_contracts.py` | no unresolved ref/readable drift | Passed |
| Regression | `./gradlew test` | zero failures | Passed |
| Acceptance | `./gradlew acceptanceTest` | zero failures before Phase 5 | Passed: 24/24 |
| Migration safety | Testcontainers PostgreSQL + Flyway | empty schema and restart pass | Passed previously; current rerun blocked (Docker unavailable) |
| Config/secrets | focused tests and source scan | no committed secrets/default production password | Passed; only empty environment defaults |
| Dependency vulnerability/license | resolved dependency report | no known critical/high unresolved; licences reviewed | Blocked: dependency graph resolves, but no vulnerability/licence scanner is installed |
| Performance | reproducible local load probe | NFR-1 | Partial: warmed HTTP/in-memory p95 1085 ms; PostgreSQL run blocked |

## Migration evidence

| Migration | Empty schema | Current-app compatibility | Expand/contract plan | Evidence |
|---|---|---|---|---|
| V1–V3 | Passed previously; blocked in current VM | N/A, initial service schema | additive initial schema | `ModerationMigrationTest` |

## Performance evidence

| Operation | NFR | Baseline | Current result | Workload |
|---|---|---|---|---|
| `POST /internal/v1/moderations` | p95 <= 2 s | none | Partial: 1085 ms | Warm paced 50 RPS through HTTP/Basic auth with 1 s provider stub and in-memory store; JDBC run unavailable |
