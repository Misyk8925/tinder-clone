# Bug: Photos ignores repo-root `.env`

State: **fixed** · Severity: minor · Service: `services/photos`

## Expected

`cd services/photos && uvicorn ...` loads AWS/CDN settings from the repo-root `.env` (and `.env.local` if present), the same file Compose uses.

## Actual

`Settings` used `env_file=".env"`, which is cwd-relative. From `services/photos` that is `services/photos/.env`, not the root file.

## Repro

1. Put `AWS_S3_BUCKET=...` only in repo-root `.env`.
2. `cd services/photos && uvicorn app.main:app --port 8070`.
3. Settings keep the empty default bucket and use in-memory storage.

## Root cause

pydantic-settings resolved `.env` against the process working directory. Nothing walked up to the repo root. Compose still injects the root file into the container, so only host `uvicorn` was wrong.

## Regression

`tests/test_config.py` — `test_given_only_the_repo_root_env_when_cwd_is_the_service_dir_then_settings_use_the_root_file`
