# Release Evidence: local-media-storage

## Checklist

- **P5.1 Build / validation:**
  - `services/photos` Docker image built cleanly (`python:3.12-slim-bookworm` + `/var/lib/photos` owned by `app:app`).
  - `services/photos`: 62/62 `pytest` tests passed.
  - `services/gateway`: `GatewayRouteConfigurationTest` passed.
  - `services/deck-read`: `PhotoObjectRefTest` + `DeckPhotoUrlRewriterTest` passed.
- **P5.2 Security scan of affected surface:**
  - `test_dockerfile.py` passed (`libpcre2-8-0` CVE fix intact).
  - Path traversal, missing/tampered/expired HMAC signature, and missing signing secret all covered by automated tests.
- **P5.3 Deploy:** `N/A` — production VPS deployment was not requested.
- **P5.4 Smoke / affected runtime checks:**
  - Single-container `photos` Docker smoke test against real mounted host directory passed (`E2E_DOCKER_SMOKE_PASSED`).
- **P5.5 Rollback note:**
  - Set `PHOTOS_STORAGE_BACKEND=s3` (with `AWS_S3_BUCKET` and AWS credentials) in `.env` and restart `photos` to return immediately to AWS S3 storage. Existing S3 objects and `/api/v1/photos/**` contracts are untouched.
- **P5.6 Monitoring / observation window:** `Not yet observed` (not deployed to VPS).
- **P5.7 Docs / runbook:**
  - Updated `docs/contracts/http/photos.openapi.yaml`, `docs/contracts/http/photos.openapi.md`, `.env.prod.example`, and `docs/features/local-media-storage/*`.
  - **VPS backup note (RISK-3):** Back up the `photos-data` Docker volume (or bind-mount host directory) alongside Postgres backups and monitor VPS disk utilization.
- **P5.8 Open-risk view:** Empty (RISK-1, RISK-2, RISK-4 Mitigated; RISK-3 Accepted with runbook mitigation).
