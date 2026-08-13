# Contract index — deck-read-cqrs

## Canonical artifacts

| Boundary | Canonical artifact | Readable view | Validation command | Compatibility impact |
|---|---|---|---|---|
| HTTP | [`docs/contracts/http/deck-read.openapi.yaml`](../../../contracts/http/deck-read.openapi.yaml) | [`deck-read.openapi.md`](../../../contracts/http/deck-read.openapi.md) | `ruby scripts/validate-deck-read-contracts.rb` | Additive v2; v1 success remains a bare array and is marked deprecated. |
| Profiles maintenance HTTP | [`profiles-deck-card-backfill.openapi.yaml`](../../../contracts/http/profiles-deck-card-backfill.openapi.yaml) | [`profiles-deck-card-backfill.openapi.md`](../../../contracts/http/profiles-deck-card-backfill.openapi.md) | `ruby scripts/validate-deck-read-contracts.rb` | Internal mTLS start/resume/status API; not called on startup. |
| Events | [`docs/contracts/events/deck-read.asyncapi.yaml`](../../../contracts/events/deck-read.asyncapi.yaml) | [`deck-read.asyncapi.md`](../../../contracts/events/deck-read.asyncapi.md) | `ruby scripts/validate-deck-read-contracts.rb` | Adds one topic; existing `swipe-saved` and `match.created` wire payloads are unchanged. |
| Profiles backfill state | Phase-4 migration in the existing Profiles schema | [`deck-card-backfill.md`](../../../contracts/data/deck-card-backfill.md) | Contract validator now; migration/Testcontainers in Phase 4 | Adds checkpoint state and nullable outbox linkage; no new database. |

The feature does not add a database. It adds durable backfill checkpoint state to the existing Profiles PostgreSQL schema so a Read Cluster recovery survives a Profiles restart. The entity/outbox linkage and explicit mTLS maintenance endpoint are implemented in Phase 4.

## Cross-cutting decisions

- Authentication: Deck Read validates the existing JWT; `sub` is `viewerUserId`. The local profile projection maps it to `viewerProfileId`, which is the ID used by Deck Redis, ensure, swipe/match events and every `dr:viewer:{viewerProfileId}:...` key. Gateway forwards the bearer token unchanged.
- Versioning: HTTP v2 is additive. Event compatibility within `profile.deck-card-projection.v1` is additive-only; breaking changes require a new topic/message version.
- Pagination: cursor is opaque, generation-bound and safe to repeat; limit is 1–100.
- Rate limits: no new quota is introduced; existing Gateway policy remains in force.
- Logging: eventId, generation, viewer/profile IDs and lag may be logged; card bio, preferences, URLs and JWT must not be logged.
- Delivery: Kafka is at-least-once. Consumers reject older versions/generations and no-op duplicate eventIds.

## Future production validation

- [MIS-14: production Read Cluster backup/restore validation](https://linear.app/mischa8925/issue/MIS-14/future-scope-production-read-cluster-backuprestore-validation)
- [MIS-15: deployed backfill, Kafka catch-up and repeat recovery](https://linear.app/mischa8925/issue/MIS-15/future-scope-deployed-backfill-kafka-catch-up-and-repeat-recovery)
- [MIS-30: production rollout and v1/v2 traffic shadowing](https://linear.app/mischa8925/issue/MIS-30/future-scope-deck-read-production-rollout-and-v1v2-traffic-shadowing)
