# Events — <feature-slug>

Status: DRAFT | APPROVED <date>
Spec: `asyncapi.yaml` (source of truth — this file must match it)

## <domain>.<entity>.<verb>.v1

| Property | Value |
|----------|-------|
| Topic/channel | `<topic>` |
| Producer | `<service>` |
| Consumers | `<services>` |
| Partition key | `<tenantId>` |
| Delivery | at-least-once |
| Ordering | per key |
| Retention | 7 days |
| DLQ | `<topic>.dlq` after 5 retries |

**Payload**

```json
{
  "eventId": "uuid",
  "occurredAt": "2026-07-12T10:00:00Z",
  "tenantId": "uuid",
  "data": {}
}
```

**Consumer idempotency:** <how a duplicate is detected and ignored>

**Why this partition key / delivery semantics:** <reasoning the spec file can't hold>

**Versioning:** additive fields only within v1; breaking change ⇒ `.v2` channel, dual-publish for <N> weeks.
