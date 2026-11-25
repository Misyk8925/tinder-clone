# Local VPS Media Storage & Pluggable Storage Abstraction

Repo-local tracker: [`00-state.md`](00-state.md). Linear is not authorized in this environment.

This folder indexes canonical artifacts for this feature and does not copy specs or tests.

| Artifact | Purpose |
|---|---|
| [`00-state.md`](00-state.md) | Tracker state, decisions, risk register, phase ledger, and gates |
| [`01-concept.md`](01-concept.md) | Problem, prose contracts, FRs/NFRs, cheapest design, rejected alternatives |
| `02-contracts/` | (Phase 2) Links to canonical `docs/contracts/http/photos.openapi.yaml` and Compose/gateway contracts |
| `03-behaviour/` | (Phase 3) FR/error traceability to executable `pytest` and JUnit tests |
| `04-implementation/` | (Phase 4) Slice plan, iteration log, and QA evidence |
| `05-release/` | (Phase 5) Build, validation, and rollback evidence |

## Canonical files affected

- `docs/contracts/http/photos.openapi.yaml`
- `services/photos/app/storage/` (new storage abstraction package: base contract, factory/registry, `local`, `s3`, `memory`)
- `services/photos/app/config.py`, `services/photos/app/api.py`, `services/photos/app/main.py`
- `services/gateway/src/main/resources/application.yml`, `services/gateway/src/main/java/com/tinder/gateway/SecurityConfig.java`
- `services/deck-read/src/main/java/com/tinder/deckread/client/PhotoObjectRef.java`
- `docker-compose.yml`, `.env.prod.example`
