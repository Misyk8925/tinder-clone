# Swipe Rewind Events (`swipe-revoked` & `match.created`)

Canonical spec: [`swipe-revoked.asyncapi.yaml`](swipe-revoked.asyncapi.yaml)
Java shared type: [`SwipeRevokedEventV1.java`](../../../services/tinder-contracts/src/main/java/com/tinder/contracts/event/v1/SwipeRevokedEventV1.java)
Requirements: `FR-1`, `FR-2`, `FR-4`, `FR-6`, `FR-7`, `FR-8`, `FR-9`, `NFR-2`, `NFR-3`, `NFR-5`, `NFR-6`
Compatibility: Additive topic `swipe-revoked`; `swipe-created`, `swipe-saved`, and `match.created` schemas are unchanged.

## Topic summary

| Topic | Producer | Consumers | Partition key | Delivery | DLT |
|---|---|---|---|---|---|
| `swipe-revoked` (10 partitions) | `swipes-go` | `consumer`, `deck`, `deck-read` | `profile1Id` (swiper UUID) | at-least-once | `swipe-revoked.dlt` |
| `match.created` (existing) | `consumer` | `match`, `deck-read`, **`swipes-go`** (new group `swipes-match-projection-go`) | `profile1Id` | at-least-once | `match.created.dlt` |

### Why partition key = `profile1Id`
All swipes and revocations initiated by one viewer land on the same partition, keeping per-viewer ordering deterministic inside `swipe-revoked` and matching the existing `swipe-created` / `swipe-saved` key.

## `SwipeRevokedEventV1` payload

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | `string (uuid)` | Yes | Unique revocation event ID (= HTTP `revocationEventId`) |
| `revokedSwipeEventId` | `string (uuid)` | Yes | `eventId` of the original `SwipeCreatedEvent` |
| `profile1Id` | `string (uuid)` | Yes | Swiper profile ID (Kafka partition key) |
| `profile2Id` | `string (uuid)` | Yes | Target profile ID to return to position 0 |
| `originalDecision` | `boolean` | Yes | `true` = like, `false` = pass |
| `swipedAt` | `int64` | Yes | Original swipe timestamp (epoch ms) |
| `revokedAt` | `int64` | Yes | Revocation timestamp (epoch ms) |

## Consumer idempotency & ordering rules

1. **`consumer`** (group `${spring.kafka.consumer.group-id}`):
   - Inserts `eventId` into `swipe_revocations` (`PRIMARY KEY (event_id)`). Duplicate `eventId` → immediate no-op.
   - Defensive match guard (`RISK-1`): if `swipe_records` already has `isMutualMatch = true` for the normalized pair, records status `IGNORED_MATCH` and does not modify `swipe_records`, `pending_likes`, or Redis.
   - Cross-topic ordering guard (`RISK-3`): if a newer `swipe_events` record for `(profile1Id, profile2Id)` exists with `created_at > revokedAt`, records status `IGNORED_STALE` and preserves the newer swipe.
   - Otherwise resets only swiper's decision column (`decision1` or `decision2`) to `NULL`, removes `(liked_user_id=profile2Id, liker_profile_id=profile1Id)` from `pending_likes` when `originalDecision=true` (or restores reciprocal `(liked_user_id=profile1Id, liker_profile_id=profile2Id)` when `originalDecision=false` and target still has `decision=true`), removes `profile2Id` from Redis `swipes:exists:{profile1Id}`, and increments Micrometer counter `consumer_swipes_revoked_total`.
2. **`deck`** (group `${spring.kafka.consumer.group-id}`):
   - Skips if `profile2Id` is in `deck:deleted`.
   - Removes any existing member for `profile2Id` in `deck:{profile1Id}`, computes `maxScore + 1` (or `1e9` if empty), adds `DeckEntry.fresh(profile2Id)` with that score, and adds `profile1Id` to `deck:contains:{profile2Id}`. Safe on duplicate delivery.
3. **`deck-read`** (group `deck-read-swipe-revoked-v1`):
   - Atomic Redis Lua mutation: if `profile2Id` is in `dr:viewer:{profile1Id}:matched` → no-op (`RISK-1`); if `dr:viewer:{profile1Id}:swipes` has a swipe with `timestamp > revokedAt` → no-op (`RISK-3`).
   - Otherwise removes `profile2Id` from `dr:viewer:{profile1Id}:swipes` and `dr:viewer:{profile1Id}:repeat-candidates`, writes `dr:viewer:{profile1Id}:rewound = "{profile2Id}|{revokedAt}"` (TTL 24h), and enqueues materialization (`SWIPE_REVOKED`) which places `profile2Id` at index 0.
4. **`swipes-go` on `match.created`** (group `swipes-match-projection-go`):
   - Normalizes `(min(p1,p2), max(p1,p2))` and executes `INSERT ... ON CONFLICT DO NOTHING` into `swipe_matches`.

## DLT & PII policy
- Failed messages after bounded retries go to `swipe-revoked.dlt` / `match.created.dlt`.
- Payloads, logs, and DLT headers carry **zero PII** — only UUIDs, timestamps, boolean decision, partition/offset, and sanitized exception messages (`NFR-5`).
