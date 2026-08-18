# Contract conventions

Project-root `docs/contracts/conventions.md`. Written once; later features read it.

| Type | Format | Check |
|---|---|---|
| HTTP | OpenAPI 3.1 | `spectral lint` |
| Events | AsyncAPI 2.x | N/A until first event |
| Data | Flyway | migrate CI |

Errors: `SCREAMING_SNAKE_CASE`. Events: `domain.entity.verb.v1`.
