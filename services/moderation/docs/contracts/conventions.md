# Contract conventions

This project uses the following canonical contract formats:

| Boundary | Format | Canonical location | Validation |
|---|---|---|---|
| HTTP | OpenAPI 3.1 | `src/main/resources/contracts/openapi.yaml` | YAML parse plus OpenAPI structural checks |
| Events | AsyncAPI 3.0 | `src/main/resources/contracts/asyncapi.yaml` | YAML parse plus AsyncAPI structural checks |
| Data | PostgreSQL SQL migrations | `src/main/resources/db/migration/` | PostgreSQL/Testcontainers migration test in implementation phase |

Rules:

- `/internal/v1` is the first internal HTTP compatibility boundary.
- HTTP fields may be added compatibly; removing or changing existing fields requires a new API version.
- Event names and payloads carry an explicit `schemaVersion`; incompatible changes use a new topic/version.
- Kafka delivery is at-least-once, so consumers and publishers use stable message IDs and idempotency records.
- Published policy versions and stored evidence are immutable; corrections create new records or activation pointers.
- Raw content, conversation context, passwords, authorization headers, and provider payloads must never be logged.
