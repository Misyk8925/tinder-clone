# Moderation data catalog

Canonical migration: [`V1__moderation_service.sql`](../../../src/main/resources/db/migration/V1__moderation_service.sql)

PostgreSQL is the source of truth. This internal service has no tenant dimension in the
current contract; access isolation is enforced at service/network level. Adding tenancy
requires a schema and authorization change, not an implicit convention.

| Table | Purpose | Sensitive data and retention | Important queries/indexes |
|---|---|---|---|
| `moderation_policy_version` | Mutable drafts and immutable published policy documents. | Actor identifiers retained with policy audit history. | Primary lookup by semantic `policy_version`. |
| `moderation_policy_activation` | One active version per global/content/locale scope and its rollback pointer. | No raw user content. | `UNIQUE NULLS NOT DISTINCT (content_type, locale)` enforces one pointer per scope. |
| `moderation_decision` | Idempotent moderation result, content snapshot, context, policy, and evidence. | Raw normalized text, image URLs and context expire after 90 days by default; evidence retention is separately configurable. | Newest-first page, decision/policy filter, and content history indexes. |
| `moderation_review_task` | Human-review state with optimistic aggregate version. | Moderator note may contain sensitive data and follows decision retention unless legal policy says otherwise. | Open queue index by status and creation time. |
| `moderation_audit_log` | Append-only record of policy and review mutations. | Actor identifier; details must not contain raw content or credentials. | Target history index. |
| `moderation_outbox` | Durable event publication after state commit. | Payload can contain evidence; raw context is excluded from result events. Published rows are purged by an operational retention job. | Partial pending index by next attempt. |
| `moderation_login_attempt` | Shared failed-login count and temporary lock. | Username and lock state; no password/hash. | Primary lookup by username. |

## Integrity rules

- Published policy rows must have publisher and publication timestamp.
- Application code rejects any update to `policy_json` after publication.
- Decision idempotency keys and source Kafka message IDs are unique when present.
- A decision creates at most one review task.
- Decision confidence stays in `[0,1]`.
- Policy/review aggregate versions back the HTTP `If-Match` contract.
- Audit and outbox records are inserted in the same transaction as each mutation.

## Cleanup

A scheduled cleanup clears `normalized_text`, `image_urls_json`, and `context_json` when
`raw_content_expires_at` is reached; it does not delete immutable evidence required for
audit. The exact production retention remains a release gate because legal approval is
outside this repository.

