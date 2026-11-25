# State: local-media-storage

Fallback repo-local tracker (Linear is not authorized in this environment).
Current phase: **5 — Release evidence complete (deploy N/A)**.

## Phase milestones

| Phase | Artifact | Status | Approved by | Date |
|---|---|---|---|---|
| 1 Concept | [01-concept.md](01-concept.md) | Approved | User (`confirmed.`) | 2026-11-24 |
| 2+3 Contracts + behaviour | [02-contracts/README.md](02-contracts/README.md), [03-behaviour/README.md](03-behaviour/README.md) | Approved | User (`confirmed.`) | 2026-11-24 |
| 4 Implementation | [04-implementation/plan.md](04-implementation/plan.md) | Done | — | 2026-11-24 |
| 5 Release | [05-release/checklist.md](05-release/checklist.md) | Done (deploy N/A) | — | 2026-11-24 |

## Decisions

| Date | Decision | Status | Source |
|---|---|---|---|
| 2026-11-24 | Serve locally stored photos via HMAC-signed expiring download endpoint in `photos` service, routed through `gateway` | Approved | User frontier answer (A1) |
| 2026-11-24 | Persist local media in `docker-compose.yml` using a Docker named volume (`photos-data:/var/lib/photos`) with env-configurable path | Approved | User frontier answer (A2) |
| 2026-11-24 | Verify e2e local photo saving with automated `pytest` e2e against real disk + single `photos` Docker container smoke check | Approved | User frontier answer (A3) |

## Risk register (Open-risk view: empty)

| ID | Status | Likelihood | Impact | Risk | Mitigation |
|---|---|---|---|---|---|
| RISK-1 | Mitigated | Low | High | Path traversal in local storage keys or `GET /media/{key}` | Enforced via `keys.validate_key` + `LocalFilesystemStorage._resolve_key` (`Path.resolve().relative_to(root)`); verified by unit & acceptance tests. |
| RISK-2 | Mitigated | Medium | Medium | `deck-read` `PhotoObjectRef.parse` skipped `/media/` prefix | Updated `PhotoObjectRef.parse` + `PhotoObjectRefTest` green. |
| RISK-3 | Accepted | Low | Medium | Single-node VPS disk capacity / volume backups | Documented in `05-release/checklist.md` runbook; replication out of scope for v1 (owner: VPS operator). |
| RISK-4 | Mitigated | Low | Low | Docker daemon availability for smoke check | Docker daemon available; live single-container smoke check passed (`E2E_DOCKER_SMOKE_PASSED`). |

## Phase ledger — `full-feature-delivery` / phases 4 & 5

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P4.9 Session/agent handoff | N/A | Same session continued through Phase 4 and Phase 5. |
| P5.3 Deploy | N/A | Production VPS deploy not requested/authorized. |
| P5.6 Monitoring / observation window | Deferred | Not yet observed — pending user deployment to VPS. |
| P4.1 Slice plan | Done | `04-implementation/plan.md` (Slice 1 + Slice 2). |
| P4.2 Primary evidence/red | Done | `GatewayRouteConfigurationTest` failed red before Slice 2; acceptance suite added in Phase 3. |
| P4.3 Implementation against approved contracts | Done | `services/photos/app/storage/*`, `api.py`, `service.py`, `config.py`, `keys.py`, `main.py`, `Dockerfile`, `gateway`, `deck-read`, `docker-compose*.yml`, `.env.prod.example`. |
| P4.4 Test levels | Done | Unit + component + e2e (`pytest` 62/62), live Docker container smoke (`E2E_DOCKER_SMOKE_PASSED`), Gateway & Deck-Read JUnit green; full Compose stack `N/A` per `AGENTS.md`. |
| P4.5 Error-path evidence | Done | `400 INVALID_IMAGE`, `403 INVALID_SIGNATURE`, `404 PHOTO_NOT_FOUND`, `503 PHOTO_STORAGE_ERROR` all tested & passing. |
| P4.6 Fresh-context review | Done | Self-review performed against contracts and engineering standards (no independent reviewer in session). |
| P4.7 Targeted defect review | Done | Found and fixed `docker-compose.demo.yml` `PHOTOS_STORAGE_BACKEND: "memory"` override (`04-implementation/plan.md`). |
| P4.8 Quality gates / qa-metrics | Done | Recorded in `04-implementation/plan.md`. |
| P4.10 Combined-diff review | Done | Reviewed full diff across Python, Java, Compose, and OpenAPI. |
| P5.1 Build / validation | Done | `services/photos` Docker image built; 62 pytest + Gateway + Deck-Read JUnit green. |
| P5.2 Security scan of affected surface | Done | `test_dockerfile.py` CVE check + path traversal + HMAC constant-time tests green. |
| P5.4 Smoke / affected runtime checks | Done | Single-container Docker local-disk e2e smoke test passed (`E2E_DOCKER_SMOKE_PASSED`). |
| P5.5 Rollback note | Done | `05-release/checklist.md` (switch `PHOTOS_STORAGE_BACKEND=s3`). |
| P5.7 Docs/changelog/runbook | Done | `photos.openapi.yaml`, `photos.openapi.md`, `.env.prod.example`, `05-release/checklist.md`. |
| P5.8 Open-risk view empty; no blocker | Done | All risks Mitigated or Accepted; 0 blockers. |
