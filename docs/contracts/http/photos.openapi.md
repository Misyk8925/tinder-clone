# Photos Service HTTP API — readable contract

Canonical source: [`photos.openapi.yaml`](photos.openapi.yaml).

Internal-only. Profiles and Match call it on the Docker network. There is no gateway route and no JWT. Callers already authenticated the user.

Object keys are `{namespace}/{ownerId}/{storageId}/{variant}.jpg`. `namespace` is `photos` for profile albums and `chat/photos` for conversation messages.

## POST /api/v1/photos

Serves FR-1–FR-5. Not idempotent: each call creates a new storage id.

Request: multipart `file` + `owner_id` + optional `namespace`.

Success `201` returns the storage id, four public URLs, the original key, stored JPEG size, original pixel size, and SHA-256 of the uploaded bytes.

| Status | Code | When | Retry? |
|---|---|---|---|
| 201 | — | Variants stored | No, new storage id each time |
| 400 | `INVALID_IMAGE` | Missing file, bad type, over 5 MB, undecodable bytes, below 300 px, above 4096 px, unknown namespace | No |
| 503 | `PHOTO_STORAGE_ERROR` | S3 write failed | Yes, backoff |

## DELETE /api/v1/photos/{storageId}

Serves FR-6. Missing objects are not an error.

| Status | Code | When | Retry? |
|---|---|---|---|
| 204 | — | Delete attempted for original/large/medium/small | Safe to repeat |
| 400 | `INVALID_IMAGE` | Unknown namespace | No |
| 503 | `PHOTO_STORAGE_ERROR` | Storage client failure other than a missing object | Yes, backoff |

## GET /api/v1/photos/{storageId}/download-url

Serves FR-7. `size` defaults to `medium`.

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | Presigned GET URL | Yes |
| 400 | `INVALID_IMAGE` | Unknown size or namespace | No |
| 503 | `PHOTO_STORAGE_ERROR` | Presign failed | Yes, backoff |

## POST /api/v1/photos/cleanup-orphaned

Serves FR-8. Body lists storage ids the caller still catalogues. Other objects under the owner prefix are deleted. Failures are swallowed and `deleted` may be zero.

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | Cleanup finished | Yes |
| 400 | `INVALID_IMAGE` | Unknown namespace | No |

| FR | Endpoint |
|---|---|
| FR-1–FR-5 | POST /api/v1/photos |
| FR-6 | DELETE /api/v1/photos/{storageId} |
| FR-7 | GET /api/v1/photos/{storageId}/download-url |
| FR-8 | POST /api/v1/photos/cleanup-orphaned |
