# Contract index — swipe-rewind

Tracker: [`00-state.md`](../00-state.md) · Concept: [`01-concept.en.md`](../01-concept.en.md) / [`01-concept.ru.md`](../01-concept.ru.md)

## Canonical artifacts (P2.1–P2.11)

| Ledger | Boundary | Canonical artifact | Readable mirror | Validation command | Compatibility impact |
|---|---|---|---|---|---|
| P2.1 | HTTP API | [`swipes-rewind.openapi.yaml`](../../../contracts/http/swipes-rewind.openapi.yaml) | [`swipes-rewind.openapi.md`](../../../contracts/http/swipes-rewind.openapi.md) | `python3 scripts/validate-swipe-rewind-contracts.py` | Additive `POST /api/v1/swipes/rewind`; existing `/api/v1/swipes` & `/super` unchanged. |
| P2.2 | Events (Kafka) | [`swipe-revoked.asyncapi.yaml`](../../../contracts/events/swipe-revoked.asyncapi.yaml) | [`swipe-revoked.asyncapi.md`](../../../contracts/events/swipe-revoked.asyncapi.md) | `python3 scripts/validate-swipe-rewind-contracts.py` | Additive topic `swipe-revoked` (+ `.dlt`) and new `swipes-go` consumer group on unchanged `match.created`. |
| P2.3 | Websockets | `N/A` | `N/A` | — | No WebSocket channels or frames added/modified. |
| P2.4 | Data / SQL migrations | [`V3_swipes_rewind.sql`](../../../../migrations/migration/V3_swipes_rewind.sql), [`V2_consumer_swipe_revocations.sql`](../../../../migrations/migration/V2_consumer_swipe_revocations.sql) | [`swipe-rewind.md`](../../../contracts/data/swipe-rewind.md) | `python3 scripts/validate-swipe-rewind-contracts.py` | Additive idempotent tables/indexes + Redis `dr:viewer:{id}:rewound`; no destructive DDL. |
| P2.5 | UI screen / route | `/discover` (`DiscoverComponent`, existing auth guard) | Screen-state table below | `npm test` (Vitest acceptance) | Additive Rewind control in Discover action bar & empty-deck state. |
| P2.6 | Shared component API | `N/A` | `N/A` | — | Uses existing `lucide-angular` `rotate-ccw`; no shared component input/output changes. |
| P2.7 | Client state | [`profile.model.ts`](../../../../clients/tinder-client/src/app/core/models/profile.model.ts) (`RewindRequest`, `RewindResponse`, `RewindErrorCode`) | Client-state table below | `npm test`, `npm run build` | Additive TS interfaces; no breaking changes to existing models. |
| P2.8 | Config / feature flags | Service YAML/env keys (listed below) | Config table below | `go test ./...`, service config tests | Additive env/topic/group keys with safe defaults; no temporary feature flag. |
| P2.9 | Scheduled / background jobs | `N/A` | `N/A` | — | Event-driven Kafka consumers only (covered under P2.2); no new cron/scheduled job. |
| P2.10 | Model / ML I/O | `N/A` | `N/A` | — | No ML model involved. |
| P2.11 | Shared code contracts | [`SwipeRevokedEventV1.java`](../../../../services/tinder-contracts/src/main/java/com/tinder/contracts/event/v1/SwipeRevokedEventV1.java) | Consumer & rebuild order below | `mvn -B test` + `mvn -B -DskipTests install` | Additive Java record in `tinder-contracts` (`1.0.0`); 0 source/binary breaking changes. |

---

## P2.5 UI screen / route contract (`/discover`, FR-10)

- **Route & guards:** Existing `/discover` route behind existing Keycloak auth guard (`authGuard`).
- **Inputs:** Current `profiles()` deck, `currentIndex()`, `myProfile()`, and in-memory `lastSwipe` record.
- **Accessibility:** Native `<button type="button" aria-label="Rewind" [disabled]="!canRewind()">` with `rotate-ccw` icon, keyboard focusable and activatable via `Enter`/`Space`.

| View state | Trigger | Shows | Next action |
|---|---|---|---|
| `disabled-no-swipe` | No swipe performed yet (or last swipe already rewound) | Rewind button rendered `disabled` left of Pass | Perform a swipe |
| `available` | Last swipe ≤ 5 min old, not matched, not yet rewound, not in-flight | Active Rewind button left of Pass | Click Rewind → `POST /api/v1/swipes/rewind` |
| `empty-deck-rewindable` | Deck exhausted (`currentIndex >= profiles.length`) and `canRewind()` is true | Active Rewind button on the "You're all caught up" screen alongside Refresh | Click Rewind → card returns at index 0 |
| `pending` | `POST /api/v1/swipes/rewind` in flight | Rewind button `disabled` (prevents double-click race) | Wait for HTTP response |
| `disabled-expired` | 5-minute client timer fires or server returns `409 REWIND_WINDOW_EXPIRED` | Rewind button `disabled` (plus toast on `409`) | Continue swiping |
| `disabled-matched` | Match modal / `watchForMatch` detects match or server returns `409 SWIPE_ALREADY_MATCHED` | Rewind button `disabled` (plus toast on `409`) | Continue swiping or open chat |
| `error-conflict-other` | Server returns `409 NOT_LATEST_SWIPE` or `404 NO_SWIPE_TO_REWIND` | Toast explaining rewind is unavailable; button `disabled` | Continue swiping |
| `error-rate-limited` | Server returns `429 RATE_LIMITED` | Toast asking user to retry later; button stays enabled if still within 5 min | Retry after cooldown |
| `error-unavailable` | Server returns `503 SERVICE_UNAVAILABLE` or network error (`status === 0`) | Toast indicating temporary error; button stays enabled if still within 5 min | Retry Rewind |
| `error-auth` | Server returns `401 UNAUTHENTICATED` or `403 PROFILE_OWNERSHIP_DENIED` | Existing auth error handling / toast; button `disabled` | Re-authenticate |

---

## P2.7 Client state contract (`DiscoverComponent`)

| State field | Shape | Writers | Lifetime | Cleared by |
|---|---|---|---|---|
| `lastSwipe` | `{ profile: DeckCard; decision: boolean; swipedAtMs: number; matched: boolean; rewound: boolean } \| null` | `performSwipe` (sets on accepted swipe), `watchForMatch` (sets `matched=true`), `rewindLastSwipe` (sets `rewound=true` on 200 or `null`/expired on 404/409), 5m expiry timer | Component instance (in-memory) | `ngOnDestroy`, logout/route leave, next swipe (overwrites), `rewound=true`, `matched=true`, 5m timer |
| `rewinding` | `signal<boolean>` | `rewindLastSwipe` (`true` on start, `false` in `finalize`) | Single HTTP call | Request completion / `ngOnDestroy` |
| `toast` (existing) | `signal<string \| null>` | `rewindLastSwipe` error handler via `showToast(...)` | 4 s | Existing `toastTimer` / `ngOnDestroy` |

*Page-reload fallback:* If `200 OK` returns a `profile2Id` not currently in local `profiles()` memory (e.g. restored session), `DiscoverComponent` reloads the deck from `deck-read` (`GET /api/v2/deck`) so the rewound card appears at index 0.

---

## P2.8 Config / environment keys

| Key | Service | Type / Default | Owner | Off-behaviour |
|---|---|---|---|---|
| `SWIPES_REVOKED_TOPIC` | `swipes-go` | string / `swipe-revoked` | swipes-go | Must exist in Kafka (`AllowAutoTopicCreation: false`) |
| `SWIPES_MATCH_CREATED_TOPIC` | `swipes-go` | string / `match.created` | swipes-go | Consumed when `SWIPES_MATCH_CONSUMER_ENABLED=true` |
| `SWIPES_MATCH_CONSUMER_GROUP` | `swipes-go` | string / `swipes-match-projection-go` | swipes-go | — |
| `SWIPES_MATCH_CONSUMER_ENABLED` | `swipes-go` | bool / `true` | swipes-go | If `false`, match projection disabled (tests only) |
| `SWIPES_REWIND_WINDOW` | `swipes-go` | duration / `5m` | swipes-go | Enforces 5-minute rewind cutoff (FR-3) |
| `app.kafka.topic.swipe-revoked` | `consumer` | string / `swipe-revoked` | consumer | — |
| `kafka.topics.swipe-revoked` | `deck` | string / `swipe-revoked` | deck | — |
| `DECK_READ_SWIPE_REVOKED_GROUP_ID` | `deck-read` | string / `deck-read-swipe-revoked-v1` | deck-read | — |

---

## P2.11 Shared code contracts & rebuild order

- **Changed shared type:** `com.tinder.contracts.event.v1.SwipeRevokedEventV1` (additive record, `TOPIC = "swipe-revoked"`).
- **Compile-time consumers:** `services/deck`, `services/deck-read` (`services/profiles` also depends on `tinder-contracts`, unaffected).
- **Wire-only consumers (no Maven dependency):** `services/swipes-go` (Go JSON producer), `services/consumer` (standalone Docker build context `./services/consumer`, local JSON-compatible DTO).
- **Rebuild order:**
  1. `cd services/tinder-contracts && mvn -B -DskipTests install`
  2. `services/deck`, `services/deck-read` (resolve updated `tinder-contracts:1.0.0`)
  3. `services/swipes-go`, `services/consumer`, `services/gateway`, `clients/tinder-client` (independent).
- **Breaking impact:** None (0 source- or binary-breaking changes).

---

## P2.12 Consumer matrix & breaking-change policy

| Boundary | Producer | Consumers | Breaking? |
|---|---|---|---|
| `POST /api/v1/swipes/rewind` | `swipes-go` (via `gateway`) | `clients/tinder-client` | No (additive) |
| `swipe-revoked` (`SwipeRevokedEventV1`) | `swipes-go` | `consumer`, `deck`, `deck-read` | No (additive) |
| `match.created` | `consumer` | `match`, `deck-read`, `swipes-go` (added) | No (schema unchanged) |
| `swipes_db` (`V3_swipes_rewind.sql`) | `swipes-migrations` / `init-databases.sh` | `swipes-go` | No (additive `IF NOT EXISTS`) |
| `consumer_db` (`V2_consumer_swipe_revocations.sql`) | `consumer-migrations` / `init-databases.sh` | `consumer` | No (additive `IF NOT EXISTS`) |
| `dr:viewer:{id}:rewound` | `deck-read` worker | `deck-read` worker (`DeckMaterializationService`) | No (additive Redis key) |

**Breaking-change policy:** All HTTP, Kafka, SQL, and shared library changes in `swipe-rewind` are strictly additive. Existing endpoints and topics are not modified.

---

## P2.13 Cross-cutting rules

- **Auth/authz:** Gateway enforces JWT authentication on `/api/**`; `swipes-go` validates JWT and resolves `profile1Id` strictly from `sub` via `ProfileIDForUser`. No `PremiumOrAdminFilter` (`Q1`).
- **Versioning:** HTTP under `/api/v1/swipes/rewind`; Kafka contract named `SwipeRevokedEventV1` on `swipe-revoked`.
- **Rate limits:** Gateway `RoleBasedRateLimitFilter` on `swipes-rewind`: basic `100/h`, premium `500/h`, admin `1000000000/h`, anon `0/h`.
- **Logging & PII:** Log only UUIDs (`eventId`, `revokedSwipeEventId`, `profile1Id`, `profile2Id`), HTTP status, and error codes; never log JWT, names, bios, emails, or URLs (`NFR-5`).

---

## P2.14 & P2.15 Error coverage & owned risks

- Every HTTP error code (`400`, `401`, `403`, `404`, `409 x3`, `429`, `503`) is mapped 1-to-1 in [`swipes-rewind.openapi.md`](../../../contracts/http/swipes-rewind.openapi.md) and the UI view-state table above, and receives an executable acceptance test in Phase 3.
- Owned risks tracked in [`00-state.md`](../00-state.md): `RISK-1` (**Accepted** by owner), `RISK-2`, `RISK-3`, `RISK-4` (Open, mitigated by Phase-4 implementation).
