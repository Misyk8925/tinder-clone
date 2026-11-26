# Release Checklist: swipe-rewind — branch `feat/swipe-rewind` (base `310f09b`)

Pre-deploy engineering handoff checklist (`full-feature-delivery`, Phase 5). Deployment to production (`lunari.misyk.tech`) was not requested in v1; unexecuted deployment/CI steps are explicitly marked `N/A`, `Blocked`, or `Not yet observed`.

## Phase ledger — `full-feature-delivery` / phase 5 (non-`Done` first)

| Sub-step | Status | Evidence or reason |
|---|---|---|
| P5.2 Security scan | Blocked | Local diff secret scan PASS (`NO_HARDCODED_SECRETS_FOUND`), JWT `sub` ownership + Gateway `RoleBasedRateLimitFilter` + zero-PII DLT/logs verified in tests & review; CI SCA (`npm audit`/`osv`), container Trivy, and SAST run in CI pipeline before deploy |
| P5.3 Deploy | N/A | Production/staging deployment was not requested (`01-concept` Out of scope) |
| P5.4 Smoke / affected runtime checks | Blocked | Requires deployed environment (`lunari.misyk.tech`) running `feat/swipe-rewind`; post-deploy smoke must run `POST /api/v1/swipes/rewind` health/auth check + `e2e-record --pr <n>` (`RISK-5`) |
| P5.6 Monitoring / observation window | Not yet observed | Pre-deploy; 24h post-deploy observation window required for `NFR-1` (`p95 < 150 ms @ 50 rps`), `NFR-2` (`< 2 s p95` Kafka → `deck-read`), and counters below |
| P5.1 Build / validation | Done | All 7 service/client suites + Angular production build + contract validator PASS (see [`qa-metrics.md`](../04-implementation/qa-metrics.md)) |
| P5.5 Rollback note | Done | Expand-only additive migrations allow safe code-only image rollback (details below) |
| P5.7 Docs / runbook | Done | Canonical OpenAPI/AsyncAPI/Data docs + mirrors + runbook section below + updated `00-state.md`, `README.md`, `03-behaviour/README.md`, `04-implementation/*` |
| P5.8 Open risk / blocker | Done | Open-risk view has 0 undecided risks (`RISK-1` Accepted by user; `RISK-2..RISK-4` Mitigated; `RISK-5` Deferred to post-deploy `e2e-record`); 0 open blocker bugs (7/7 review findings fixed with regression tests) |

## Build & validation order (for CI / container builds)

1. `services/tinder-contracts`: `mvn -B test && mvn -B -DskipTests install` (must run before `deck` and `deck-read`).
2. `services/gateway`, `services/swipes-go`, `services/consumer`, `services/deck`, `services/deck-read`, `clients/tinder-client`: run unit/acceptance suites and production builds (`npm run build` for Angular).
3. Contract check: `python3 scripts/validate-swipe-rewind-contracts.py`.
4. Migrations before app rollout:
   - `swipes_db`: [`migrations/migration/V3_swipes_rewind.sql`](../../../../migrations/migration/V3_swipes_rewind.sql) (additive tables `swipe_history`, `swipe_revocations`, `swipe_matches` + indexes + idempotent `swipes_app` role grants).
   - `consumer_db`: [`migrations/migration/V2_consumer_swipe_revocations.sql`](../../../../migrations/migration/V2_consumer_swipe_revocations.sql) (additive table `swipe_revocations` + indexes + idempotent `consumer_app` role grants).
5. Kafka topics before app rollout (`docker-compose.yml` `deck-read-topics` / Kafka admin):
   - `swipe-revoked` (10 partitions), `swipe-revoked.dlt` (10 partitions), `match.created.dlt` (3 partitions).

## Rollback plan

- **Trigger:** Elevated `5xx` / `503 SERVICE_UNAVAILABLE` on `POST /api/v1/swipes/rewind`, consumer DLT growth on `swipe-revoked.dlt` / `match.created.dlt`, or `NFR-1`/`NFR-2` SLO breach during rollout.
- **Procedure (code-only rollback is safe):**
  1. Roll back `clients/tinder-client` and `services/gateway` (removes Rewind UI button and route immediately; existing `POST /api/v1/swipes` is untouched).
  2. Roll back `services/swipes-go`, `services/consumer`, `services/deck`, `services/deck-read` images to the previous git SHA.
  3. **Do NOT drop SQL tables or Kafka topics during rollback:** `V3_swipes_rewind.sql` and `V2_consumer_swipe_revocations.sql` are strictly expand-only (`CREATE TABLE IF NOT EXISTS`); old service versions ignore the new tables and `swipe-revoked` topic safely.
  4. Verify normal swipe/deck smoke checks after rollback.

## Runbook

- **What it does:** Allows any authenticated user to undo their single most recent swipe (`like` or `pass`) within 5 minutes via `POST /api/v1/swipes/rewind`, unless the swipe already produced a match. Emits append-only `SwipeRevoked` (`swipe-revoked` topic) consumed by `consumer`, `deck`, and `deck-read` to compensate read models and return the profile at deck index `0`.
- **Canonical contracts:**
  - HTTP: [`docs/contracts/http/swipes-rewind.openapi.yaml`](../../../contracts/http/swipes-rewind.openapi.yaml) ([readable](../../../contracts/http/swipes-rewind.openapi.md))
  - Events: [`docs/contracts/events/swipe-revoked.asyncapi.yaml`](../../../contracts/events/swipe-revoked.asyncapi.yaml) ([readable](../../../contracts/events/swipe-revoked.asyncapi.md))
  - Data: [`docs/contracts/data/swipe-rewind.md`](../../../contracts/data/swipe-rewind.md)
- **Key metrics to watch (observation window: 24h post-deploy):**
  - `swipes-go`: `swipes_revoked_total{decision}`, `swipes_rewind_rejected_total{reason}` (rate & error breakdown), HTTP latency (`NFR-1: p95 < 150 ms`).
  - `consumer`: `consumer_swipes_revoked_total{result}` (`applied`, `ignored_match`, `ignored_stale`, `duplicate`).
  - `deck-read`: `deck_read_materialization_duration` & Kafka consumer lag on `DECK_READ_SWIPE_REVOKED_GROUP_ID` (`NFR-2: < 2 s p95`).
- **Top 3 failure modes & operator actions:**
  1. **`503 SERVICE_UNAVAILABLE` spikes on `/api/v1/swipes/rewind`:** Check `swipes_db` connectivity and Kafka `swipe-revoked` topic existence (`AllowAutoTopicCreation` is `false` in `swipes-go`). Verify `V3_swipes_rewind.sql` and `swipe-revoked` topic exist; if Kafka is degraded, roll back Gateway route / client until broker recovers.
  2. **Rewound card does not reappear at top of deck (`deck-read` / `deck` lag):** Inspect `swipe-revoked.dlt` and consumer lag for `deck` and `deck-read` (`DECK_READ_SWIPE_REVOKED_GROUP_ID`). Check Redis key `dr:viewer:{viewerId}:rewound`; messages in `swipe-revoked.dlt` contain non-PII UUIDs (`revocationEventId`, `revokedSwipeEventId`, `profile1Id`, `profile2Id`) and are safe to replay after fixing the consumer issue.
  3. **False `200 OK` during mutual-match race (`RISK-1`):** `swipes-go` reads local `swipe_matches` fed by `match.created` (~1 s outbox + Kafka lag). If `match.created` consumer lags, check `match.created.dlt` and `swipes-go` consumer group; `consumer` (`IGNORED_MATCH`) and `deck-read` (`dr:viewer:{id}:matched`) preserve match integrity even if `swipes-go` lagged.
