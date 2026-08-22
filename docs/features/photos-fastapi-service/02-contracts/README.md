# Contract index — photos-fastapi-service

| Boundary | Canonical | Readable view | Check | Compatibility |
|---|---|---|---|---|
| HTTP | `docs/contracts/http/photos.openapi.yaml` | `docs/contracts/http/photos.openapi.md` | pytest API tests | new internal API |
| Events | N/A | — | — | Profiles still emits deck-card events after catalogue writes |
| Websocket | N/A | — | — | no socket |
| Data | N/A | — | — | no schema change |

Authz: internal network only. Versioning: v1. Rate limits: unchanged at the gateway. Logs: no image bytes.
