# Swipes Rewind HTTP API

Canonical spec: [`swipes-rewind.openapi.yaml`](swipes-rewind.openapi.yaml)
Requirements: `FR-1`, `FR-2`, `FR-3`, `FR-4`, `FR-5`, `FR-6`, `NFR-1`, `NFR-3`, `NFR-4`, `NFR-5`, `NFR-6`
Compatibility: Additive endpoint. `POST /api/v1/swipes` and `POST /api/v1/swipes/super` stay unchanged.

## Endpoint

- **Method & path:** `POST /api/v1/swipes/rewind`
- **Gateway route:** `swipes-rewind` → `SWIPES_SERVICE_URL` (`http://swipes:8040`), predicates `Path=/api/v1/swipes/rewind`, `Method=POST`.
- **Auth & roles:** Valid Keycloak JWT required (`BearerAuth`). Available to all authenticated users (`USER_BASIC`, `USER_PREMIUM`, `ADMIN`); no `PremiumOrAdminFilter`. Caller `profile1Id` is resolved strictly from JWT `sub` (never trusted from the request body).
- **Rate limits (Gateway `RoleBasedRateLimitFilter`):** `basicCapacity: 100/h`, `premiumCapacity: 500/h`, `adminCapacity: 1000000000/h`, `anonCapacity: 0/h`.
- **Idempotency:** Safe to retry. If the caller's single latest swipe was already revoked, returns `200 OK` with `alreadyRevoked: true`, the original `revocationEventId`, and publishes zero new Kafka events. Depth is strictly 1 (never rewinds the second-to-last swipe).

## Request

Body is optional (`""` or `{}`).

| Field | Type | Required | Rule |
|---|---|---|---|
| `swipeEventId` | `string (uuid)` | No | If provided, must match the user's latest swipe `event_id`; otherwise `409 NOT_LATEST_SWIPE`. |

## Success response (`200 OK`)

| Field | Type | Meaning |
|---|---|---|
| `revokedSwipeEventId` | `string (uuid)` | `eventId` of the original swipe |
| `revocationEventId` | `string (uuid)` | `eventId` of the compensating `SwipeRevoked` event |
| `profile2Id` | `string (uuid)` | Profile returned to position 0 of the viewer's deck |
| `decision` | `boolean` | Original swipe decision (`true` = like, `false` = pass) |
| `revokedAt` | `string (date-time)` | UTC timestamp of the revocation |
| `alreadyRevoked` | `boolean` | `false` on first rewind, `true` on idempotent repeat |

## Error table

| Status | Code | When | Retry? |
|---|---|---|---|
| `400` | `INVALID_PAYLOAD` | Malformed JSON, unknown field, or invalid UUID in `swipeEventId` | No |
| `401` | `UNAUTHENTICATED` | Missing, expired, or invalid JWT bearer token | No |
| `403` | `PROFILE_OWNERSHIP_DENIED` | Authenticated user has no active profile ownership mapping | No |
| `404` | `NO_SWIPE_TO_REWIND` | User has never made a swipe | No |
| `409` | `REWIND_WINDOW_EXPIRED` | More than 5 minutes have elapsed since the latest swipe | No |
| `409` | `SWIPE_ALREADY_MATCHED` | Latest swipe already produced a mutual match | No |
| `409` | `NOT_LATEST_SWIPE` | Provided `swipeEventId` does not match the user's latest swipe | No |
| `429` | `RATE_LIMITED` | Gateway `RoleBasedRateLimitFilter` exceeded | Yes, after `Retry-After` |
| `503` | `SERVICE_UNAVAILABLE` | `swipes_db`, profile ownership lookup, or Kafka producer unavailable | Yes, with backoff |

## Consumers

- **Producer / Server:** `services/swipes-go` (proxied by `services/gateway`).
- **Consumer / Client:** `clients/tinder-client` (`SwipeService.rewind` → `DiscoverComponent`).
