# Contracts Index: local-media-storage

Canonical specs stay in their project locations and are linked below.

## Canonical contracts

| Boundary | Canonical artifact | Readable view | Compatibility impact |
|---|---|---|---|
| HTTP API (`photos`) | [`docs/contracts/http/photos.openapi.yaml`](../../../contracts/http/photos.openapi.yaml) | [`docs/contracts/http/photos.openapi.md`](../../../contracts/http/photos.openapi.md) | Additive only: adds `GET /media/{key}` and error codes `INVALID_SIGNATURE`, `PHOTO_NOT_FOUND`. All existing `/api/v1/photos/**` contracts unchanged. |
| Gateway routing | [`services/gateway/src/main/resources/application.yml`](../../../../services/gateway/src/main/resources/application.yml) & [`SecurityConfig.java`](../../../../services/gateway/src/main/java/com/tinder/gateway/SecurityConfig.java) | This index | Additive route `GET /media/**` → `${PHOTOS_SERVICE_URL:http://localhost:8070}` with `permitAll()` (HMAC-authorized). |
| Deck-Read URL parsing | [`PhotoObjectRef.java`](../../../../services/deck-read/src/main/java/com/tinder/deckread/client/PhotoObjectRef.java) | This index | Additive: accepts optional leading `media/` path segment alongside existing `/photos/...`. |
| Runtime / Compose config | [`docker-compose.yml`](../../../../docker-compose.yml) & [`.env.prod.example`](../../../../.env.prod.example) | This index | Backward-compatible: adds `PHOTOS_STORAGE_BACKEND`, `PHOTOS_LOCAL_ROOT`, `PHOTOS_PUBLIC_BASE_URL`, `PHOTOS_SIGNING_SECRET`, and `photos-data` volume; relaxes `AWS_*` to optional defaults for `local` mode. |
| Events | N/A | — | No Kafka/event schema changes. |
| Websockets | N/A | — | No WebSocket changes. |
| Database schema | N/A | — | No DB tables or columns changed; photo catalogue stays in Profiles. |

## Cross-cutting rules

- **Authz:** `/api/v1/photos/**` keeps strict `X-Internal-Auth` constant-time secret verification (fail closed with `503` when unset). `GET /media/{key}` requires a valid unexpired HMAC-SHA256 `sig` over `{key}\n{exp}` using `PHOTOS_SIGNING_SECRET` (fallback `PHOTOS_INTERNAL_AUTH_SECRET`); never uses JWT so browser `<img>` tags work directly.
- **Versioning & compatibility:** Additive change under existing `/api/v1/photos` and new `/media/{key}` path. Existing `s3` and `memory` configurations continue working without env changes.
- **Rate limits & quotas:** Upload size (5 MB), pixel dimensions (300–4096 px), and allowed types (`image/jpeg,image/png,image/webp`) remain unchanged. `GET /media/**` is read-only static variant delivery with `Cache-Control: private, max-age=<remaining>`.
- **Logging & PII:** Never log `PHOTOS_INTERNAL_AUTH_SECRET`, `PHOTOS_SIGNING_SECRET`, or full signed `sig` query parameters.
