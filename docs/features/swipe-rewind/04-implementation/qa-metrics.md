# QA Metrics — swipe-rewind

Final pre-release snapshot after all 6 implementation slices and fresh-context combined-diff review fixes (branch `feat/swipe-rewind`, base `origin/main` @ `310f09b`, 2026-11-25).

| Dimension | Status | Evidence |
|---|---|---|
| Contract validation | **PASS** | `python3 scripts/validate-swipe-rewind-contracts.py` (OpenAPI 3.1.0, AsyncAPI 2.6.0, mirrors, SQL migrations, `SwipeRevokedEventV1.java`) |
| Shared contract (`tinder-contracts`) | **PASS** | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ../gateway/mvnw -B test && ../gateway/mvnw -B -DskipTests install` → `14/14 PASS` (`SwipeRevokedEventV1AcceptanceTest` 2/2) |
| S1 — `services/gateway` | **PASS** | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -B test` → `14/14 PASS` (`SwipeRewindRouteAcceptanceTest` 3/3, `RoleBasedRateLimitFilterTest` 3/3, `GatewayApplicationTests` w/ Testcontainers Redis) |
| S2 — `services/swipes-go` | **PASS** (1 skipped integration) | `docker run --rm ... golang:1.25-alpine go test -v ./...` → all packages PASS (`TestFeatureSwipeRewindHTTPBoundary` 11/11, `TestMineWrapsNotFoundAsErrProfileNotFound`, `TestSendRecordsHistoryWithSameEventIDAsKafkaCommand`, `TestRewindRejectsNilOwnerProfileWith403`; 1 skipped: `TestProfilesIntegration` requiring `SWIPES_INTEGRATION_DATABASE_URL`/`REDIS_ADDR`) |
| S3 — `services/consumer` | **PASS** | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -B -Dtest=SwipeRewindAcceptanceTest,SwipeServiceTest test` → `19/19 PASS` (`SwipeRewindAcceptanceTest` 8/8 incl. out-of-order & duplicate-counter regression tests + `SwipeServiceTest` 11/11) |
| S4 — `services/deck` | **PASS** | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -B -Dtest=SwipeRewindDeckAcceptanceTest,DeckServiceTest test` → `16/16 PASS` (`SwipeRewindDeckAcceptanceTest` 4/4 + `DeckServiceTest` 12/12) |
| S5 — `services/deck-read` | **PASS** | `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ../gateway/mvnw -B -Dtest=SwipeRewindDeckReadAcceptanceTest,DeckMaterializationServiceTest test` → `11/11 PASS` (`SwipeRewindDeckReadAcceptanceTest` 6/6 incl. Lua ordering & DLT serializer regression tests + `DeckMaterializationServiceTest` 5/5) |
| S6 — `clients/tinder-client` | **PASS** | `npm test -- --watch=false && npm run build` → `23/23` test files, `112/112` tests PASS (`discover.rewind.acceptance.spec.ts` 12/12) + production build `dist/tinder-client` PASS |
| Migration safety (`V3_swipes_rewind.sql`, `V2_consumer_swipe_revocations.sql`) | **PASS** | Expand-only additive DDL (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, idempotent `DO $$ ... GRANT ... $$` role guards); wired in `init-databases.sh`, `run-sql-migration.sh`, and `docker-compose.yml` one-shot jobs |
| Accessibility (`FR-10` Rewind button) | **PASS** | Native `<button type="button">` with `aria-label="Rewind"`, explicit `[disabled]` binding on card bar & empty-deck view, Lucide `RotateCcw` registered in `lucide-icons.ts`, verified in `discover.rewind.acceptance.spec.ts` |
| Reconciliation / idempotency (`NFR-3`) | **PASS** | Duplicate HTTP (`alreadyRevoked: true`, no duplicate event) & duplicate/out-of-order Kafka events verified across `swipes-go`, `consumer`, `deck`, and `deck-read` |
| Secret scan (local diff) | **PASS** | `git diff` scan for credentials/keys/tokens → `NO_HARDCODED_SECRETS_FOUND` |
| CI SCA / Container Trivy / SAST | **Blocked** | Not available locally; runs in CI pipeline before deploy |
| Full Compose stack integration | **Blocked** | Per `AGENTS.md`, full Docker Compose stack (Kafka/Keycloak/Redis/Postgres) is not started by default |
| NFR-1 (`p95 < 150 ms @ 50 rps`) | **Not yet observed** | Requires deployed load test + observation window |
| NFR-2 (`< 2 s p95` Kafka → `deck-read`) | **Not yet observed** | Requires running Kafka + Redis stack / deployed observation window |
| Recorded browser e2e (`e2e-record`) | **Blocked** | Target `lunari.misyk.tech` does not run `feat/swipe-rewind` yet (`RISK-5`); run `e2e-record --pr <n>` after deploy |
| Confirmed defects (fresh-context review) | **0 open** (7 found, 7 fixed) | Combined-diff reviewer (`b6f98d69-8f51-4b54-b802-9b52618b76c1`): 2 blockers, 1 major, 4 minor — all 7 fixed with regression tests in [`log.md`](log.md) |
