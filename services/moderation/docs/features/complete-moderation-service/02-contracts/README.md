# Contract index — complete-moderation-service

## Canonical artifacts

| Boundary | Canonical file | Readable view | Validation | Compatibility impact |
|---|---|---|---|---|
| HTTP | [`openapi.yaml`](../../../../src/main/resources/contracts/openapi.yaml) | [`moderation-api.md`](../../../contracts/http/moderation-api.md) | `python3 scripts/validate_contracts.py` | First internal v1 API; no existing API is broken. |
| Events | [`asyncapi.yaml`](../../../../src/main/resources/contracts/asyncapi.yaml) | [`moderation-events.md`](../../../contracts/events/moderation-events.md) | `python3 scripts/validate_contracts.py` | First v1 topics; surrounding services require separate integration. |
| Data | [`V1__moderation_service.sql`](../../../../src/main/resources/db/migration/V1__moderation_service.sql) | [`data-catalog.md`](../../../contracts/data/data-catalog.md) | PostgreSQL migration test in Phase 4 | Introduces the service's initial PostgreSQL schema. |

Project-wide formats and evolution rules are in
[`docs/contracts/conventions.md`](../../../contracts/conventions.md).

## Cross-cutting decisions

- Authentication: configured BCrypt users; form session for GUI; HTTP Basic for JSON API.
- Authorization: `VIEWER`, `MODERATOR`, and `POLICY_ADMIN` roles.
- Versioning: `/internal/v1`, `.v1` topics, and `schemaVersion: 1`.
- Concurrency: `Idempotency-Key` for moderation mutations and `If-Match` for policy/review aggregates.
- Limits: 1 MiB body, 20 context messages, 16 KiB context text, list limit at most 100.
- Logging: no raw content, context, credentials, authorization header, or provider payload.

## Open risks

See [`../00-state.md`](../00-state.md), especially provider credentials, retention approval,
and deployment-specific Kafka names/ACLs.
