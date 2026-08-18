# HTTP — bio-max-length

Canonical: `openapi.yaml`. Auth: existing profile owner.

## PUT /profiles/{id}

Serves FR-1. Idempotent.

Request: `{ "bio": "a".repeat(501) }` → 400

| Status | Code | When | Retry? |
|---|---|---|---|
| 204 | — | bio length 0–500 | — |
| 400 | BIO_TOO_LONG | length > 500 | No |

| FR | Endpoint |
|---|---|
| FR-1 | PUT /profiles/{id} |
