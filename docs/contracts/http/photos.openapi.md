# Photos Service HTTP API — readable contract

Canonical source: [`photos.openapi.yaml`](photos.openapi.yaml).

Service-to-service endpoints (`/api/v1/photos/**`) are internal-only (called by Profiles, Match, and Deck-Read over the Docker network using `X-Internal-Auth`). Signed media delivery (`GET /media/{key}`) is routed through Gateway without JWT and authorized by HMAC-SHA256 query signature (`exp` + `sig`).

Storage backend is selected via `PHOTOS_STORAGE_BACKEND` (`local`, `s3`, `memory`). When unset, `s3` is used if `AWS_S3_BUCKET` is non-empty, else `memory`.

Object keys are `{namespace}/{ownerId}/{storageId}/{variant}.jpg`. `namespace` is `photos` for profile albums and `chat/photos` for conversation messages.

## POST /api/v1/photos

Not idempotent: each call creates a new storage id.

Request: multipart `file` + `owner_id` + optional `namespace`.

Success `201` returns the storage id, four public URLs, the original key, stored JPEG size, original pixel size, and SHA-256 of the uploaded bytes.

| Status | Code | When | Retry? |
|---|---|---|---|
| 201 | — | Variants stored | No, new storage id each time |
| 400 | `INVALID_IMAGE` | Missing file, bad type, over 5 MB, undecodable bytes, below 300 px, above 4096 px, unknown namespace | No |
| 401 | — | Missing or invalid `X-Internal-Auth` | No |
| 503 | `PHOTO_STORAGE_ERROR` | Backend write failed or internal auth secret unset | Yes, backoff |

## DELETE /api/v1/photos/{storageId}

Missing objects are not an error.

| Status | Code | When | Retry? |
|---|---|---|---|
| 204 | — | Delete attempted for original/large/medium/small | Safe to repeat |
| 400 | `INVALID_IMAGE` | Unknown namespace or invalid key | No |
| 401 | — | Missing or invalid `X-Internal-Auth` | No |
| 503 | `PHOTO_STORAGE_ERROR` | Storage failure other than a missing object | Yes, backoff |

## GET /api/v1/photos/{storageId}/download-url

`size` defaults to `medium`. Returns an S3 presigned URL (`s3` backend) or an HMAC-signed `/media/{key}?exp=...&sig=...` URL (`local` backend).

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | Signed expiring GET URL | Yes |
| 400 | `INVALID_IMAGE` | Unknown size or namespace | No |
| 401 | — | Missing or invalid `X-Internal-Auth` | No |
| 503 | `PHOTO_STORAGE_ERROR` | Signing failed or secret unset | Yes, backoff |

## POST /api/v1/photos/download-urls

Batch equivalent of `GET /api/v1/photos/{storageId}/download-url` used by Deck-Read.

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | List of signed expiring GET URLs | Yes |
| 400 | `INVALID_IMAGE` | Unknown size or namespace | No |
| 401 | — | Missing or invalid `X-Internal-Auth` | No |
| 503 | `PHOTO_STORAGE_ERROR` | Signing failed | Yes, backoff |

## POST /api/v1/photos/cleanup-orphaned

Body lists storage ids the caller still catalogues. Other objects under the owner prefix are deleted. Failures are swallowed and `deleted` may be zero.

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | Cleanup finished | Yes |
| 400 | `INVALID_IMAGE` | Unknown namespace | No |
| 401 | — | Missing or invalid `X-Internal-Auth` | No |

## GET /media/{key}

Publicly routed through Gateway (`GET /media/**` → `photos:8070`). Serves JPEG bytes from the active storage backend after verifying HMAC-SHA256 signature (`sig`), expiration (`exp`), and key/path safety.

| Status | Code | When | Retry? |
|---|---|---|---|
| 200 | — | Valid signature & object exists; returns `image/jpeg` + `Cache-Control: private, max-age=<remaining>` | Yes (while unexpired) |
| 400 | `INVALID_IMAGE` | Invalid key format, unknown namespace/variant, or path traversal (`..`) | No |
| 403 | `INVALID_SIGNATURE` | Missing, malformed, mismatched, or expired `exp`/`sig` | No (request fresh download URL) |
| 404 | `PHOTO_NOT_FOUND` | Object does not exist in storage | No |
| 503 | `PHOTO_STORAGE_ERROR` | Signing secret unconfigured or storage read failure | Yes, backoff |
