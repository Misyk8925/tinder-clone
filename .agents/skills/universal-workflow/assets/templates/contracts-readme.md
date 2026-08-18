# Contract index — bio-max-length

Keep all four boundary rows. Unused = `N/A` with a reason.

| Boundary | Canonical | Readable view | Check | Compatibility |
|---|---|---|---|---|
| HTTP | `services/profiles/.../openapi.yaml` | `openapi.md` | `spectral lint` | additive `BIO_TOO_LONG` |
| Events | N/A | — | — | no event |
| Websocket | N/A | — | — | no socket |
| Data | N/A | — | — | no schema change |

Authz: existing profile owner. Versioning: additive error code. Rate limits: unchanged. Logs: bio body not logged.
