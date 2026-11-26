# Concept: Swipe Rewind

Status: APPROVED (2026-11-25) · Slug: `swipe-rewind`

A Discover user cannot undo an accidental pass or like because `consumer`, `deck`, and `deck-read` hide the profile immediately. We add `POST /api/v1/swipes/rewind` in `swipes-go` backed by append-only tables in `swipes_db` and a local `match.created` projection, publish a compensating `SwipeRevoked` event (`tinder-contracts`) without deleting the original swipe, update `consumer`/`deck`/`deck-read` read models to return the profile to position 0, and add a Rewind button in `clients/tinder-client`.

## In scope

| ID | Part | Done when |
|---|---|---|
| FR-1 | Authorized user rewinds their latest swipe (`like` or `pass`) within 5 minutes via `POST /api/v1/swipes/rewind` through `gateway` | Swipe ≤ 5 min old → `200 OK` (`alreadyRevoked: false`) and one `SwipeRevoked` event on `swipe-revoked` |
| FR-2 | Repeat Rewind on the same already-revoked latest swipe is idempotent and depth stays 1 | Second call returns `200 OK` (`alreadyRevoked: true`), publishes 0 new events, and does not undo the earlier swipe |
| FR-3 | Reject Rewind when more than 5 minutes passed since the latest swipe | Swipe > 5 min old → `409 Conflict` (`REWIND_WINDOW_EXPIRED`), 0 events published |
| FR-4 | Reject Rewind when the latest swipe already matched; protect matches on race | Local match projection → `409 Conflict` (`SWIPE_ALREADY_MATCHED`); `consumer` and `deck-read` never undo an existing match |
| FR-5 | Reject Rewind when no swipes exist or optional `swipeEventId` is not the latest swipe | No swipes → `404 Not Found`; mismatched `swipeEventId` → `409 Conflict` (`NOT_LATEST_SWIPE`) |
| FR-6 | Record rewind as an append-only compensating event `SwipeRevoked` (`tinder-contracts`, key `profile1Id`) without deleting the swipe | Original swipe row stays intact in `swipes_db` and `consumer_db`; `SwipeRevoked` JSON contract matches across producers and consumers |
| FR-7 | Compensate `consumer` read models and revocation metrics on `SwipeRevoked` | Non-matched pair: swiper decision reset to `NULL` in `swipe_records`, `pending_likes` updated (remove revoked like / restore reciprocal like on pass rewind), `targetId` removed from Redis `swipes:exists:{swiperId}`, counter incremented |
| FR-8 | Return the rewound profile to position 0 in `deck` and `deck-read` | `deck` adds `profile2Id` at top score in `deck:{viewerId}` + reverse index; `deck-read` clears `swipes`/`repeat-candidates`, prioritizes `rewound`, and serves the card at index 0 |
| FR-9 | Allow swiping the rewound profile again (`like` or `pass`) | Second swipe updates `swipes-go`, `consumer`, `deck`, and `deck-read` while keeping the first swipe and `SwipeRevoked` in history |
| FR-10 | Add Rewind button (`rotate-ccw`, `aria-label="Rewind"`) in `clients/tinder-client` Discover action bar and empty-deck state | Disabled when unavailable/expired/matched/in-flight; `200 OK` places card at index 0 (or reloads deck); `409` shows toast and disables button |
| NFR-1 | Low-latency Rewind endpoint | `POST /api/v1/swipes/rewind` p95 < 150 ms at 50 rps (`swipes_db` + Kafka produce only) |
| NFR-2 | Fast deck visibility after Rewind | Card returns to position 0 in `deck-read` in < 2 s p95 at normal Kafka lag (< 500 ms) |
| NFR-3 | Safe at-least-once and HTTP retry handling | 100% of duplicate HTTP Rewind calls and duplicate Kafka `SwipeRevoked` deliveries produce no duplicate events or state drift |
| NFR-4 | Authz and rate limiting | Valid JWT required; `profile1Id` derived from JWT `sub`; `gateway` applies `RoleBasedRateLimitFilter` (basic 100/h, premium 500/h, anon 0) |
| NFR-5 | No PII leakage | 0 PII fields (names, emails, bio, JWT) in logs and `swipe-revoked.dlt` headers — UUIDs and error codes only |
| NFR-6 | Backward compatibility and zero extra infra | 0 breaking changes to existing HTTP/Kafka contracts; 0 new services, databases, or brokers |

## Out of scope

| Item | Why |
|---|---|
| Multi-step rewind (depth > 1) | v1 allows undoing only the single latest swipe |
| Unmatch / chat deletion | Matched swipes are rejected with `409 Conflict` |
| Premium gate, billing, or daily rewind quotas | Owner decision Q1: open to all authenticated users with gateway rate limits |
| New user swipe-counter table in `consumer` | Owner decision Q3: compensate existing read models and metrics only |
| Changes to `swipe-created`, `swipe-saved`, or `match.created` schemas | Existing cross-service contracts stay unchanged |
| Production deploy | Delivery stops at repo code, migrations, contracts, and tests unless deploy is requested |
