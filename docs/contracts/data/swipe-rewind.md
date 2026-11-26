# Data Catalog: Swipe Rewind

Canonical migrations:
- [`migrations/migration/V3_swipes_rewind.sql`](../../../migrations/migration/V3_swipes_rewind.sql) (`swipes_db`)
- [`migrations/migration/V2_consumer_swipe_revocations.sql`](../../../migrations/migration/V2_consumer_swipe_revocations.sql) (`consumer_db`)

Requirements: `FR-1`, `FR-2`, `FR-3`, `FR-4`, `FR-5`, `FR-6`, `FR-7`, `FR-8`, `FR-9`, `NFR-3`, `NFR-5`, `NFR-6`

## PostgreSQL tables

### 1. `swipes_db.swipe_history`
- **Owner:** `services/swipes-go` (role `swipes_app`).
- **Purpose:** Append-only log of accepted swipes so `swipes-go` knows each viewer's single latest swipe (`event_id`, `profile2_id`, `decision`, `swiped_at`) without querying `consumer`. Rows are **never deleted** (`FR-6`).
- **Tenant / ownership boundary:** Row-level by `profile1_id`, derived strictly from JWT `sub` → `profile_cache`.
- **PII:** None (UUIDs, booleans, timestamps only — `NFR-5`).
- **Retention:** Retained as append-only swipe history; future retention pruning out of scope for v1.

| Column | Type | Meaning |
|---|---|---|
| `event_id` | `UUID` (PK) | Exact `eventId` published in `swipe-created` |
| `profile1_id` | `UUID` | Swiper profile ID |
| `profile2_id` | `UUID` | Swiped target profile ID |
| `decision` | `BOOLEAN` | `true` = like, `false` = pass |
| `is_super` | `BOOLEAN` | `true` for Super Like |
| `swiped_at` | `TIMESTAMPTZ` | UTC timestamp when swipe was accepted (used for 5-minute window check) |

- **Index:** `idx_swipe_history_profile1_swiped_at (profile1_id, swiped_at DESC, event_id DESC)` — serves `SELECT ... FROM swipe_history WHERE profile1_id = $1 ORDER BY swiped_at DESC, event_id DESC LIMIT 1 FOR UPDATE` on every `POST /api/v1/swipes/rewind`.

### 2. `swipes_db.swipe_revocations`
- **Owner:** `services/swipes-go` (role `swipes_app`).
- **Purpose:** Append-only record of issued rewinds. Enforces idempotency via `UNIQUE (revoked_swipe_event_id)` so a repeated rewind for the same latest swipe returns the stored `revocation_event_id` without publishing a duplicate Kafka event (`FR-2`, `NFR-3`).
- **Tenant / ownership boundary:** Row-level by `profile1_id`.
- **PII:** None.

| Column | Type | Meaning |
|---|---|---|
| `revocation_event_id` | `UUID` (PK) | `eventId` of the compensating `SwipeRevoked` event |
| `revoked_swipe_event_id` | `UUID` (UNIQUE, FK → `swipe_history.event_id`) | Original swipe being compensated |
| `profile1_id` | `UUID` | Swiper profile ID |
| `profile2_id` | `UUID` | Target profile ID |
| `original_decision` | `BOOLEAN` | Original swipe decision |
| `swiped_at` | `TIMESTAMPTZ` | Original swipe timestamp |
| `revoked_at` | `TIMESTAMPTZ` | UTC timestamp when revocation was created |

- **Index:** `idx_swipe_revocations_profile1_revoked_at (profile1_id, revoked_at DESC)` — supports per-viewer audit lookups and diagnostics.

### 3. `swipes_db.swipe_matches`
- **Owner:** `services/swipes-go` (role `swipes_app`), populated by `swipes-match-projection-go` consuming `match.created`.
- **Purpose:** Local read projection of mutual matches for O(1) `409 SWIPE_ALREADY_MATCHED` checks without synchronous HTTP calls to `consumer` (`FR-4`, `Q2`, `NFR-1`).
- **Invariant:** Pair is always normalized with `profile1_id < profile2_id` (`CHECK (profile1_id < profile2_id)`).
- **PII:** None.

| Column | Type | Meaning |
|---|---|---|
| `profile1_id` | `UUID` (PK part 1) | Smaller UUID of the matched pair |
| `profile2_id` | `UUID` (PK part 2) | Larger UUID of the matched pair |
| `matched_at` | `TIMESTAMPTZ` | Match creation timestamp from `match.created` |

- **Index:** Composite PK `(profile1_id, profile2_id)` serves `SELECT EXISTS(SELECT 1 FROM swipe_matches WHERE profile1_id = $1 AND profile2_id = $2)`.

### 4. `consumer_db.swipe_revocations`
- **Owner:** `services/consumer` (role `consumer_app`).
- **Purpose:** Append-only compensating log and deduplication table for Kafka `swipe-revoked` consumption (`FR-6`, `FR-7`, `NFR-3`). Original `swipe_events` rows are never deleted; `swipe_records` is compensated in place by resetting only the swiper's column (`decision1` or `decision2`) to `NULL` when the pair is not a mutual match.
- **Tenant / ownership boundary:** Keyed by `(profile1_id, profile2_id)` from verified `SwipeRevoked` event.
- **PII:** None.

| Column | Type | Meaning |
|---|---|---|
| `event_id` | `UUID` (PK) | `SwipeRevokedEventV1.eventId` (dedup key) |
| `revoked_swipe_event_id` | `UUID` | Original `SwipeCreatedEvent.eventId` |
| `profile1_id` | `UUID` | Swiper profile ID |
| `profile2_id` | `UUID` | Target profile ID |
| `original_decision` | `BOOLEAN` | Original swipe decision (`true` = like, `false` = pass) |
| `swiped_at` | `TIMESTAMPTZ` | Original swipe timestamp |
| `revoked_at` | `TIMESTAMPTZ` | Revocation timestamp |
| `status` | `VARCHAR(32)` | `APPLIED`, `IGNORED_MATCH` (`RISK-1`), or `IGNORED_STALE` (`RISK-3`) |
| `processed_at` | `TIMESTAMPTZ` | Local processing timestamp |

- **Index:** `idx_consumer_swipe_revocations_pair (profile1_id, profile2_id, revoked_at DESC)` — supports ordering checks against subsequent swipes for the same pair.

## Redis key changes (no new Redis instance)

| Redis DB | Owner | Key | Change | TTL / Invariant |
|---|---|---|---|---|
| db 0 | `consumer` | `swipes:exists:{swiperId}` | `SREM {targetId}` on `APPLIED` rewind (only if key exists) | Existing 24h TTL unchanged |
| db 0 | `deck` | `deck:{viewerId}` & `deck:contains:{profileId}` | Re-add `DeckEntry.fresh(profile2Id)` with score `maxScore + 1` (or `1e9` if empty); `SADD deck:contains:{profile2Id} {viewerId}` | Existing deck TTL preserved |
| db 1 | `deck-read` | `dr:viewer:{viewerId}:swipes` & `dr:viewer:{viewerId}:repeat-candidates` | `HDEL` / `ZREM` `{profile2Id}` guarded by `!SISMEMBER matched` and `storedSwipeTs <= revokedAt` | Existing 7-day retention unchanged |
| db 1 | `deck-read` | `dr:viewer:{viewerId}:rewound` (new string key) | Value `"{profile2Id}\|{revokedAt}"`; `DeckMaterializationService` prepends `profile2Id` at index 0 of `fresh` (from `ProfileProjectionStore`). Cleared on next `SwipeSaved` or `MatchCreated` for `profile2Id`. | TTL 24h |
