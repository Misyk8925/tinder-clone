# Codex guidance

## Cloud tasks

- Treat Codex Cloud as a reproducible build, unit-test, and code-review environment.
- Do not start the full Docker Compose stack by default. It requires Kafka, Keycloak, Redis, and several databases, and is reserved for explicitly requested integration work.
- Never read, add, print, or require production `.env` files, credentials, tokens, or cloud access keys. Use fixture data and test configuration only.
- Keep agent internet access disabled unless a task explicitly requires a narrowly scoped exception.
- Work on the smallest affected service(s). State which commands were run and separate passed checks from infrastructure-dependent checks that could not run.

## Repository layout and validation

- Java services live in `services/*`; run the Maven wrapper from the affected service when it exists.
- `services/tinder-contracts` is a local Maven dependency. Install it with `mvn -B -DskipTests install` before resolving dependencies or building Deck, Deck Read, or Profiles.
- Go services are `services/location-go` and `services/swipes-go`; validate with `go test ./...` from the affected service.
- The photos service is `services/photos`; validate with `python -m pytest` after `pip install -r requirements-dev.txt`.
- The Angular client is `clients/tinder-client`; use `npm ci` for dependency installation and `npm run build` for a production build check.
- Some tests use Testcontainers, Kafka, Redis, PostgreSQL, or Quarkus Dev Services. Do not report those as passed when their required runtime was unavailable.
- Before changing a cross-service DTO or endpoint, inspect its actual consumers and preserve shared contracts unless the task explicitly changes the boundary.

## Cursor Cloud specific instructions

Scope: this Cloud environment is set up for the **`services/photos`** FastAPI service (the focus of the `photos-fastapi-service` branch). The other services (Java/Quarkus, Go, Angular) are not provisioned by the startup update script; set those up per the commands referenced above only if a task requires them.

- Python deps for photos are installed **system-wide** by the startup update script (`pip install --break-system-packages -r services/photos/requirements-dev.txt`). Ubuntu 24.04 is PEP 668 "externally managed" and `python3.12-venv` is not installed, so a venv is not used. Re-run that command after editing `requirements*.txt`.
- Installed console scripts land in `~/.local/bin`, which is **not on `PATH`**. Invoke tools as modules: `python3 -m pytest`, `python3 -m uvicorn ...`.
- Standard commands live in `services/photos/README.md`. Test: run `python3 -m pytest` from `services/photos` (tests are hermetic — no S3/DB/network/credentials, no `conftest.py`). Run dev server: `python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8070`.
- The service boots with **zero env vars**: when `AWS_S3_BUCKET` is empty it uses an in-memory storage backend, so no AWS credentials are needed for local dev or the hello-world flow. Set `AWS_S3_BUCKET` (+ creds/region, or `AWS_S3_ENDPOINT` for LocalStack/MinIO) only to exercise real S3.
- There is no lint/format tooling for photos (no ruff/flake8/black/mypy); CI runs only `python -m pytest`.
- API note when testing endpoints: `POST /api/v1/photos` requires an `owner_id` **form** field (plus `file`); `GET /api/v1/photos/{id}/download-url` and `DELETE` require `owner_id` as a **query** param. Health: `/health` and `/actuator/health`; interactive docs at `/docs`.
