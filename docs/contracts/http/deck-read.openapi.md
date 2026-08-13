# Deck Read HTTP API — readable contract

Canonical source: [`deck-read.openapi.yaml`](deck-read.openapi.yaml). Validation: `ruby scripts/validate-deck-read-contracts.rb`.

## Authentication and compatibility

Both endpoints require the existing bearer JWT. Its `sub` is `viewerUserId`; Deck Read resolves it from the local card projection to `viewerProfileId`. Deck Redis, ensure, swipe/match events and viewer snapshot keys all use that profileId. No synchronous Profiles lookup is allowed. V2 is additive. V1 is deprecated but retains its successful **bare JSON array** response and offset/limit query shape. Both versions answer only from the separate Deck Read materialized projection.

Calls are read-only and safe to repeat. Clients must treat cursor as opaque.

## GET /api/v2/deck

Serves FR-1, FR-6, FR-7, FR-8 and FR-9.

| Input | Rule |
|---|---|
| `cursor` | Optional opaque server value, 1–1024 characters. |
| `limit` | Optional integer, 1–100, default 20. |

`200 DeckPage` contains `items`, `nextCursor`, monotonic `generation`, `cursorReset` and `state`.

| State | Meaning |
|---|---|
| `READY` | Current fresh snapshot is available. |
| `REFRESHING` | A usable snapshot is served while refresh is running. |
| `DEGRADED` | Fresh is exhausted/unavailable and safe repeat cards may be included. No stale marker is added to cards. |
| `EMPTY` | Read model is ready and there are authoritatively no cards. A successful Deck ensure with no source ZSET remains BUILDING for 30 seconds, then becomes EMPTY without incrementing build failures. |

If the cursor belongs to an expired/replaced generation, the response starts from the first page of the current generation with `cursorReset=true`. A page never contains more than 100 items. `nextCursor=null` means the generation is exhausted.

`202 BUILDING` is returned when no usable snapshot exists but the initial refresh is still within the fallback window. This includes a successful Deck ensure that still returns no source ZSET during the first 30 seconds. Header `Retry-After: 2` and body `{ "state": "BUILDING", "retryAfterSeconds": 2 }` are both required.

## GET /api/v1/deck

Serves FR-2, FR-7 and FR-8. Query parameters stay `offset >= 0` and `limit 1..100` (defaults 0/20). Success is a bare `DeckCardV1[]`; no page wrapper is introduced. V1 cards are created from the same local card projection as v2, but the wire adapter preserves exactly the previous fields. Photos remain `{url,position,isPrimary}`; v2-only `photoId`, `order`, `isActive` and preferences are not added to v1.

## DeckCard

V2 exposes `profileId`, `name`, `age`, `city`, `bio`, `isActive`, preferences, hobbies and photos `{photoId,url,order}`. It deliberately exposes neither scoring nor fresh/repeat/stale provenance.

## Error contract

All errors use `application/problem+json` compatible with RFC 7807 and add stable `code`.

| Endpoint | Status | Code | When | Retry? |
|---|---:|---|---|---|
| v2 | 400 | `INVALID_CURSOR` | Cursor is malformed, tampered or unverifiable. | No, discard cursor. |
| v2 | 400 | `INVALID_LIMIT` | limit is outside 1–100. | No, fix request. |
| v1 | 400 | `INVALID_PAGINATION` | offset is negative or limit outside 1–100. | No, fix request. |
| v1/v2 | 401 | `UNAUTHENTICATED` | Bearer token is missing, expired or invalid. | No, authenticate. |
| v1/v2 | 503 | `READ_MODEL_NOT_READY` | Full recovery/backfill/catch-up has not reached its readiness gate. | Yes, backoff. |
| v2 | 503 | `DECK_TEMPORARILY_UNAVAILABLE` | 30 seconds or two consecutive refresh failures elapsed and neither fresh nor safe repeat cards exist. | Yes, backoff. |

An empty Redis/read model is never represented as `200 EMPTY` until global readiness is proven.
