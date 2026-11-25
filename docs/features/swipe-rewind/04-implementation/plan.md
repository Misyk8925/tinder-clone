# Implementation Plan — swipe-rewind

Tracker: [`00-state.md`](../00-state.md) · Contracts: [`02-contracts/README.md`](../02-contracts/README.md) · Behaviour: [`03-behaviour/README.md`](../03-behaviour/README.md)

## Slices (in dependency order)

| Slice | Observable behaviour | Dependencies | Mode | Primary acceptance check |
|---|---|---|---|---|
| S1 — Gateway route | `POST /api/v1/swipes/rewind` routed to `swipes-go` with `RoleBasedRateLimitFilter` (basic 100/h, premium 500/h, anon 0), JWT required, no Premium gate | Phases 2+3 approved | HITL (security/rate-limit config) | `SwipeRewindRouteAcceptanceTest` |
| S2 — `swipes-go` rewind + `match.created` + infra | `POST /api/v1/swipes` persists `swipe_history` with matching `eventId`; `match.created` populates `swipe_matches`; `POST /api/v1/swipes/rewind` enforces 5m/match/idempotency/depth=1 and publishes `SwipeRevoked`; `docker-compose.yml` + `init-databases.sh` wire topics & migrations | `V3_swipes_rewind.sql` (done in P2) | HITL (migrations, HTTP/Kafka boundary) | `TestFeatureSwipeRewindHTTPBoundary` + service/consumer tests |
| S3 — `consumer` compensation | `SwipeRevoked` listener writes append-only `swipe_revocations`, guards `isMutualMatch`/`IGNORED_STALE`, resets swiper decision in `swipe_records` to `NULL`, compensates `pending_likes` (incl. reciprocal restore on pass rewind), removes Redis `swipes:exists:*`, increments `consumer_swipes_revoked_total` | `V2_consumer_swipe_revocations.sql` (done in P2) | HITL (data/read-model mutation) | `SwipeRewindAcceptanceTest` + `SwipeServiceRewindTest` |
| S4 — `deck` top-of-deck restore | `SwipeRevokedEventV1` listener restores non-deleted `profile2Id` at `maxScore + 1` (or `1e9`) as `DeckEntry.fresh` in `deck:{viewerId}` + reverse index | `tinder-contracts` (`SwipeRevokedEventV1` installed) | HITL (Redis ZSET ordering) | `SwipeRewindDeckAcceptanceTest` + `DeckCache` restore test |
| S5 — `deck-read` position-0 materialization | `SwipeRevokedEventV1` clears `swipes`/`repeat-candidates` (guarded by `matched` and newer swipe), sets `dr:viewer:{id}:rewound`, enqueues materialization placing card at index 0, clears `rewound` on next swipe/match | `tinder-contracts` (`SwipeRevokedEventV1` installed) | HITL (Redis Lua + materializer) | `SwipeRewindDeckReadAcceptanceTest` + `DeckMaterializationService` rewind test |
| S6 — `tinder-client` Rewind UI | `SwipeService.rewind()` + `DiscoverComponent` Rewind button (`rotate-ccw`, `aria-label="Rewind"`) left of Pass and on empty-deck screen, 5m timer, match/in-flight disable, position-0 restore/reload fallback, error toasts | S1, S2 contracts | HITL (user-visible UI; `e2e-record` requires deployed env) | `discover.rewind.acceptance.spec.ts` + `npm run build` |

## Migrations (cannot casually roll back)

- `migrations/migration/V3_swipes_rewind.sql` (`swipes_db` — additive `CREATE TABLE IF NOT EXISTS`, expand-only, backward-compatible with previous `swipes-go` binary).
- `migrations/migration/V2_consumer_swipe_revocations.sql` (`consumer_db` — additive `CREATE TABLE IF NOT EXISTS`, expand-only, backward-compatible with previous `consumer` binary).
- Wired into `docker/postgres/init-databases.sh` and one-shot migration services in `docker-compose.yml` (`swipes-migrations`, `consumer-migrations`).

## Test-level selection (by changed risk)

- **Contract:** `validate-swipe-rewind-contracts.py` + `SwipeRevokedEventV1AcceptanceTest` (JSON wire compatibility across Go/Java).
- **Unit / Component:** `swipes-go` service/router/consumer, `gateway` YAML/security route test, `consumer` `SwipeService` & listener, `deck` `DeckCache` & consumer, `deck-read` `ViewerMutationStore` & `DeckMaterializationService`, `tinder-client` `SwipeService` & `DiscoverComponent` (Vitest).
- **Integration (Docker/Testcontainers):** `N/A` for default cloud/repo-local loop where full Compose stack is not started; migration SQL idempotency verified structurally + existing unit/mocked DB/Redis suites run cleanly.
- **System / E2E (`e2e-record`):** `Blocked` until deployed to `lunari.misyk.tech` (`RISK-5`).
- **Performance (NFR-1 / NFR-2):** `Not yet observed` locally (`Blocked` on deployed load/Kafka environment).
