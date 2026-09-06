# Moderation events v1

Canonical contract: [`src/main/resources/contracts/asyncapi.yaml`](../../../src/main/resources/contracts/asyncapi.yaml)

All topics use JSON, `schemaVersion: 1`, at-least-once delivery, and stable UUID message
identifiers. Deployment may prefix topic names but cannot change their semantic suffix.

| Topic | Message | Producer | Consumer | Key | Retention |
|---|---|---|---|---|---|
| `moderation.commands.v1` | `ModerationRequested` | Content services | Moderation service | `contentId` | 7 days |
| `moderation.results.v1` | `ModerationCompleted` | Moderation outbox | Content services | `contentId` | 30 days |
| `moderation.reviews.v1` | `ReviewChanged` | Moderation outbox | Ops/integration consumers | `reviewTaskId` | 30 days |
| `moderation.policies.v1` | `PolicyChanged` | Moderation outbox | Moderation replicas/ops | `policyVersion` | 90 days |
| `moderation.commands.dlq.v1` | `ModerationCommandRejected` | Moderation consumer | Operations | original `messageId` | 30 days |

## Delivery and ordering

- A command consumer inserts the source `messageId` with the decision transaction.
- Re-delivery of the same `messageId` reuses the stored result and does not create a new decision.
- The producer partitions commands and results by `contentId`. Ordering is guaranteed only
  inside one partition and is useful only for events carrying the same content key.
- Review events use `reviewTaskId`; policy events use `policyVersion`.
- Result, review, and policy events are written to an outbox in the same transaction as state.
- Consumers must still deduplicate by `messageId`; Kafka publication can occur more than once.

## Retry and DLQ

Transient processing failures use bounded backoff. After five unsuccessful attempts, the
consumer emits `ModerationCommandRejected` to `moderation.commands.dlq.v1` and commits the
original offset. The DLQ contains a SHA-256 payload hash and a bounded diagnostic, never the
raw content or conversation context.

## Versioning

Compatible fields may be added while `schemaVersion` remains `1`. Removing, renaming, or
changing the meaning/type of a field requires schema version 2 and a `.v2` topic. Producers
and consumers may run v1 and v2 together during migration.

