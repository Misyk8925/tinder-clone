# Implementation Plan & Evidence: local-media-storage

## Slices

| Slice | Mode | Scope | Files | Status |
|---|---|---|---|---|
| Slice 1 | AFK | Pluggable `MediaStorage` abstraction (`app/storage/`), `LocalFilesystemStorage`, `MediaUrlSigner`, `GET /media/{key}`, `Dockerfile` volume dir | `services/photos/app/storage/*`, `config.py`, `exceptions.py`, `keys.py`, `service.py`, `api.py`, `main.py`, `Dockerfile`, `tests/test_storage.py`, `tests/test_local_storage_acceptance.py` | Done |
| Slice 2 | AFK | Gateway `GET /media/**` route & security, Deck-Read `/media/` URL parsing, Compose volume & `.env.prod.example` | `services/gateway/src/main/resources/application.yml`, `SecurityConfig.java`, `services/deck-read/.../PhotoObjectRef.java`, `docker-compose.yml`, `docker-compose.demo.yml`, `.env.prod.example` | Done |

## QA & Test Evidence

| Test level | Status | Command & result |
|---|---|---|
| Unit + Component + E2E (`services/photos`) | Done | `docker build -t tinder-photos-test ./services/photos` + `python -m pytest -v` → **62 passed, 0 failed** (6.27s) |
| Live container E2E smoke (`services/photos` local volume) | Done | Single `tinder-photos-test` container with mounted host dir `/tmp/photos-e2e-smoke.*`: upload `201` → 4 JPEGs on disk → signed `GET /media/...` `200` byte-match → unsigned `403` → `DELETE` `204` + disk dir pruned + `404` (`E2E_DOCKER_SMOKE_PASSED`) |
| Unit / Route Contract (`services/gateway`) | Done | `cd services/gateway && ./mvnw -q -Dtest=GatewayRouteConfigurationTest test` → `GATEWAY_TESTS_OK` |
| Unit / Contract (`services/deck-read`) | Done | `./services/gateway/mvnw -B -q -f services/tinder-contracts/pom.xml -DskipTests install && ./services/gateway/mvnw -B -q -f services/deck-read/pom.xml -Dtest=PhotoObjectRefTest,DeckPhotoUrlRewriterTest test` → `DECK_READ_TESTS_OK` |
| Full Docker Compose stack integration | N/A | Not started per `AGENTS.md` (requires Kafka, Keycloak, Redis, Postgres). |

## Targeted Defect Review (Combined Diff)

- **Reviewer:** Self-review after full test pass (no independent reviewer in this session).
- **Leads / Defects found:** 1 lead during implementation — `docker-compose.demo.yml` relied on empty `AWS_S3_BUCKET` to select `MemoryStorage`, which would have been overridden by `docker-compose.yml`'s new `PHOTOS_STORAGE_BACKEND:-local` default. **Confirmed & fixed** by setting `PHOTOS_STORAGE_BACKEND: "memory"` in `docker-compose.demo.yml`.
- **Security checks:** Path traversal blocked at both `keys.validate_key` and `LocalFilesystemStorage._resolve_key`; HMAC-SHA256 verified with `hmac.compare_digest`; `GET /media/**` only on Gateway; `X-Internal-Auth` untouched on `/api/v1/photos/**`; no secrets in logs or repo.
