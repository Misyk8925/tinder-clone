# Deck Read events — readable contract

Canonical source: [`deck-read.asyncapi.yaml`](deck-read.asyncapi.yaml). Validation: `ruby scripts/validate-deck-read-contracts.rb`.

All deliveries are at-least-once. Deck Read commits Kafka offsets only after the Redis projection mutation succeeds. Retries are bounded; poison messages go to the corresponding `.dlt` topic with eventId, topic/partition/offset, exception category and trace metadata, but without full card PII.

Kafka is not the authoritative archive. Profiles PostgreSQL plus restartable paged outbox backfill rebuilds card/user projections. Existing Deck Redis rebuilds fresh ordering. `swipe-saved` and `match.created` retain at least seven days for repeat/exclusion recovery.

Backfill is not automatic on every Profiles startup. An operator explicitly starts the internal maintenance-job only for initial population or recovery of the Deck Read cluster.

## Topic matrix

| Topic | Producer | Deck Read role | Kafka key / ordering | Idempotency | Retention / DLT |
|---|---|---|---|---|---|
| `profile.deck-card-projection.v1` | Profiles outbox and backfill | consume | profileId; versions for one profile ordered | eventId once; version `<=` stored is no-op | Deployment retention/optional compaction; authoritative rebuild is PostgreSQL backfill; `.dlt`. |
| `swipe-saved` | Consumer outbox, unchanged | consume | profile1Id viewer, unchanged | eventId once; first viewer→candidate record uses NX so later/duplicate decision cannot reset firstSwipeAt | At least 7 days; `swipe-saved.dlt`. |
| `match.created` | Consumer outbox, unchanged | consume | profile1Id, unchanged | eventId once; matched pair exclusion is set-like | At least 7 days; `match.created.dlt`. |

There are deliberately no discovery, builder, build-requested, rebuilt or build-failed topics in this feature.

## profile.deck-card-projection.v1

Serves FR-3, FR-4, FR-7 and FR-8. Payload:

- metadata: `eventId`, `profileId`, `userId`, aggregate `version`, `occurredAt`, `operation=UPSERT|DELETE`, `source=LIVE|BACKFILL` and optional `backfillRunId`;
- complete `card`: profileId, name, age, city, bio, isActive, preferences, photos `{photoId,url,order}` and hobbies.

Create, update, patch, photo upload, photo delete and profile delete all enqueue this projection in the same Profiles transaction boundary. Photo changes increment the existing profile aggregate version.

## Exact backfill behaviour

1. An operator explicitly starts a Profiles maintenance run with a new `backfillRunId`; it is not run on every service start.
2. Profiles queries complete profile cards in stable profileId order, at most 500 rows per page.
3. For every row it builds the same projection event with the current aggregate version, `source=BACKFILL` and the run ID.
4. In one Profiles PostgreSQL transaction it inserts all page events into the existing `profile_event_outbox` and advances the durable `lastProfileId`/processed count checkpoint.
5. The existing outbox publisher sends the rows normally. Backfill code never sends directly to Kafka.
6. After a crash, the same run resumes after `lastProfileId`. A repeated page/event remains safe because Deck Read no-ops duplicate eventIds and versions not newer than the stored version.
7. Normal live profile/photo events continue concurrently. Whichever event has the highest aggregate version wins in Deck Read.
8. The run is `ENQUEUED` after the final page. Readiness still waits for that run's outbox rows to be published, Deck Read consumer lag zero, and projection count verification.

The checkpoint is stored in the existing Profiles PostgreSQL, not in the disposable Read Cluster. The data contract is [`../data/deck-card-backfill.md`](../data/deck-card-backfill.md).

Within v1, fields may only be added as optional and consumers must tolerate them. Removing/changing meaning/type requires a new message/topic version.

## swipe-saved

Serves FR-4, FR-6, FR-7 and FR-8. The current wire payload is preserved exactly: `eventId`, `profile1Id`, `profile2Id`, boolean `decision`, and epoch-millisecond `timestamp`.

Both PASS and LIKE may enter repeat history. The first seen persisted record controls `firstSwipeAt`; repeated delivery or a later decision cannot create a second candidate or extend the seven-day window. Fresh import excludes source `DeckEntry.isSwiped=true` independently.

## match.created

Serves FR-4, FR-7 and FR-8. Existing wire payload remains `eventId`, `profile1Id`, `profile2Id`, `createdAt`. Applying it removes both sides of the matched pair from active fresh/repeat snapshots and records a durable seven-day exclusion.

## Ordering and scaling

Profile projection is ordered by profileId. Its userId builds the local `viewerUserId → viewerProfileId` mapping. Existing swipe and match fields are already profileIds and their Kafka keys remain unchanged, so Deck Read uses idempotent set/NX operations rather than assuming cross-partition pair order. Multiple Deck Read replicas share one consumer group; Kafka assigns each partition to one replica at a time. Viewer mutations use `{viewerProfileId}` Redis hash tags and an atomic Lua install with monotonic generation.
