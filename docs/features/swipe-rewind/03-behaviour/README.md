# Behaviour traceability — swipe-rewind

Acceptance format: native project test frameworks with Gherkin-like `Feature:` / `Scenario: Given … when … then …` structure and explicit `Given / When / Then` comments — Go `testing` (`services/swipes-go`), JUnit 5 with `@Tag("acceptance")` (`services/tinder-contracts`, `services/gateway`, `services/consumer`, `services/deck`, `services/deck-read`), and Vitest (`clients/tinder-client`). No Cucumber dependency is introduced.

## FR & Phase-2 contract error → executable acceptance check

| FR or error | Executable check | File |
|---|---|---|
| FR-1 | `gatewayRoutesRewindToSwipesServiceWithoutPremiumGate`; `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given a latest swipe within 5 minutes (FR-1, FR-6)...` | [`SwipeRewindRouteAcceptanceTest.java`](../../../../services/gateway/src/test/java/com/tinder/gateway/SwipeRewindRouteAcceptanceTest.java), [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go) |
| FR-2 | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given the latest swipe was already rewound (FR-2, NFR-3)...` | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go) |
| FR-3 | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given the latest swipe is older than 5 minutes (FR-3)...` | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go) |
| FR-4 | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given the latest swipe already matched (FR-4)...`; `consumerHasSwipeRevokedListenerAndCompensationMethod`; `viewerMutationStoreSupportsSwipeRevokedAndRewoundPriorityKey` | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`SwipeRewindAcceptanceTest.java`](../../../../services/consumer/src/test/java/com/tinder/clone/consumer/SwipeRewindAcceptanceTest.java), [`SwipeRewindDeckReadAcceptanceTest.java`](../../../../services/deck-read/src/test/java/com/tinder/deckread/SwipeRewindDeckReadAcceptanceTest.java) |
| FR-5 | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given no prior swipes exist (FR-5)...`; `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given optional swipeEventId does not match latest swipe (FR-5)...` | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go) |
| FR-6 | `swipeRevokedEventRoundTripsCleanly`; `swipeRevokedEventIgnoresUnknownFutureFields`; `consumerConfiguresSwipeRevokedTopic`; `consumerHasSwipeRevokedListenerAndCompensationMethod` | [`SwipeRevokedEventV1AcceptanceTest.java`](../../../../services/tinder-contracts/src/test/java/com/tinder/contracts/SwipeRevokedEventV1AcceptanceTest.java), [`SwipeRewindAcceptanceTest.java`](../../../../services/consumer/src/test/java/com/tinder/clone/consumer/SwipeRewindAcceptanceTest.java) |
| FR-7 | `consumerConfiguresSwipeRevokedTopic`; `consumerHasSwipeRevokedListenerAndCompensationMethod` | [`SwipeRewindAcceptanceTest.java`](../../../../services/consumer/src/test/java/com/tinder/clone/consumer/SwipeRewindAcceptanceTest.java) |
| FR-8 | `deckConfiguresSwipeRevokedTopic`; `deckCacheSupportsRestoringRewoundProfileToTop`; `deckReadConfiguresSwipeRevokedChannel`; `viewerMutationStoreSupportsSwipeRevokedAndRewoundPriorityKey`; `deckMaterializationServicePrependsRewoundProfileAtIndexZero` | [`SwipeRewindDeckAcceptanceTest.java`](../../../../services/deck/src/test/java/com/tinder/deck/SwipeRewindDeckAcceptanceTest.java), [`SwipeRewindDeckReadAcceptanceTest.java`](../../../../services/deck-read/src/test/java/com/tinder/deckread/SwipeRewindDeckReadAcceptanceTest.java) |
| FR-9 | `swipeRepositorySupportsResettingSingleSwiperDecision`; `viewerMutationStoreSupportsSwipeRevokedAndRewoundPriorityKey` | [`SwipeRewindAcceptanceTest.java`](../../../../services/consumer/src/test/java/com/tinder/clone/consumer/SwipeRewindAcceptanceTest.java), [`SwipeRewindDeckReadAcceptanceTest.java`](../../../../services/deck-read/src/test/java/com/tinder/deckread/SwipeRewindDeckReadAcceptanceTest.java) |
| FR-10 | `SwipeService HTTP contract` (1 scenario) + `DiscoverComponent Rewind states and error handling` (3 happy/state scenarios + 8 error-state scenarios) | [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `400 INVALID_PAYLOAD` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given malformed JSON body...` | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go) |
| `401 UNAUTHENTICATED` | `securityRequiresJwtForRewindPath`; `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given an unauthenticated request...`; UI `UNAUTHENTICATED (401)` scenario | [`SwipeRewindRouteAcceptanceTest.java`](../../../../services/gateway/src/test/java/com/tinder/gateway/SwipeRewindRouteAcceptanceTest.java), [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `403 PROFILE_OWNERSHIP_DENIED` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given authenticated user has no owned profile...`; UI `PROFILE_OWNERSHIP_DENIED (403)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `404 NO_SWIPE_TO_REWIND` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given no prior swipes exist (FR-5)...`; UI `NO_SWIPE_TO_REWIND (404)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `409 REWIND_WINDOW_EXPIRED` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given the latest swipe is older than 5 minutes (FR-3)...`; UI `REWIND_WINDOW_EXPIRED (409)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `409 SWIPE_ALREADY_MATCHED` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given the latest swipe already matched (FR-4)...`; UI `SWIPE_ALREADY_MATCHED (409)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `409 NOT_LATEST_SWIPE` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given optional swipeEventId does not match latest swipe (FR-5)...`; UI `NOT_LATEST_SWIPE (409)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `429 RATE_LIMITED` | `gatewayAppliesRoleBasedRateLimitsToRewind`; UI `RATE_LIMITED (429)` scenario | [`SwipeRewindRouteAcceptanceTest.java`](../../../../services/gateway/src/test/java/com/tinder/gateway/SwipeRewindRouteAcceptanceTest.java), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |
| `503 SERVICE_UNAVAILABLE` | `TestFeatureSwipeRewindHTTPBoundary/Scenario: Given downstream store or Kafka producer fails...`; UI `SERVICE_UNAVAILABLE (503)` scenario | [`rewind_acceptance_test.go`](../../../../services/swipes-go/internal/router/rewind_acceptance_test.go), [`discover.rewind.acceptance.spec.ts`](../../../../clients/tinder-client/src/app/features/discover/discover.rewind.acceptance.spec.ts) |

## Reverse traceability (contracts & tests → FR/NFR)

- `POST /api/v1/swipes/rewind` (`swipes-rewind.openapi.yaml`) → `FR-1..FR-6`, `NFR-1`, `NFR-3..NFR-6`.
- `swipe-revoked` / `SwipeRevokedEventV1` (`swipe-revoked.asyncapi.yaml`, `SwipeRevokedEventV1.java`) → `FR-1`, `FR-2`, `FR-4`, `FR-6..FR-9`, `NFR-2`, `NFR-3`, `NFR-5`, `NFR-6`.
- `match.created` subscription in `swipes-go` → `FR-4`, `NFR-1`, `NFR-6`.
- `V3_swipes_rewind.sql` → `FR-1..FR-6`, `FR-9`, `NFR-3`, `NFR-5`.
- `V2_consumer_swipe_revocations.sql` → `FR-4`, `FR-6`, `FR-7`, `FR-9`, `NFR-3`, `NFR-5`.
- `/discover` Rewind UI contract (`02-contracts/README.md` P2.5/P2.7) → `FR-10`.

## Unautomatable / deferred-to-runtime checks (P3.6)

- **NFR-1 (`p95 < 150 ms at 50 rps`) & NFR-2 (`< 2 s p95` end-to-end Kafka lag):** verified via integration/load metrics after deployment or full stack run (`Not yet observed` in repo-local unit/component runs; tracked honestly rather than faked).
- **Real browser e2e video (`e2e-record` for `FR-10`):** requires deployed environment with the change on `lunari.misyk.tech`; per updated `universal-workflow` (`310f09b`), a run against an environment without the change counts as `Blocked` until deployed.

## Validation commands

```bash
python3 scripts/validate-swipe-rewind-contracts.py

# Java contracts & services (JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64)
(cd services/tinder-contracts && ../gateway/mvnw -B test && ../gateway/mvnw -B -DskipTests install)
(cd services/gateway && ./mvnw -B -Dtest=SwipeRewindRouteAcceptanceTest test)
(cd services/consumer && ./mvnw -B -Dtest=SwipeRewindAcceptanceTest test)
(cd services/deck && ./mvnw -B -Dtest=SwipeRewindDeckAcceptanceTest test)
(cd services/deck-read && ../gateway/mvnw -B -Dtest=SwipeRewindDeckReadAcceptanceTest test)

# Go service (via golang:1.25-alpine container when local go binary is absent)
docker run --rm -v "$PWD/services/swipes-go:/work" -w /work golang:1.25-alpine \
  go test -v ./internal/router -run TestFeatureSwipeRewindHTTPBoundary

# Angular client
(cd clients/tinder-client && npx vitest run src/app/features/discover/discover.rewind.acceptance.spec.ts)
```

## Final Phase-4 acceptance validation results (all GREEN after review fixes)

- `python3 scripts/validate-swipe-rewind-contracts.py` — **PASS** (OpenAPI 3.1.0, AsyncAPI 2.6.0, readable mirrors, `V3_swipes_rewind.sql`, `V2_consumer_swipe_revocations.sql`, `SwipeRevokedEventV1.java`).
- `services/tinder-contracts` (`../gateway/mvnw -B test && ../gateway/mvnw -B -DskipTests install`) — **14/14 PASS** (incl. `SwipeRevokedEventV1AcceptanceTest` 2/2) and installed in local `~/.m2`.
- `services/gateway` (`./mvnw -B test`) — **14/14 PASS** (incl. `SwipeRewindRouteAcceptanceTest` 3/3, `RoleBasedRateLimitFilterTest` 3/3, `GatewayApplicationTests` with Testcontainers Redis).
- `services/swipes-go` (`go test -v ./...` via `golang:1.25-alpine`) — **PASS** across all packages (incl. `TestFeatureSwipeRewindHTTPBoundary` 11/11 + service/client/router unit tests; 1 skipped: `TestProfilesIntegration` requiring live Postgres/Redis env vars).
- `services/consumer` (`./mvnw -B -Dtest=SwipeRewindAcceptanceTest,SwipeServiceTest test`) — **19/19 PASS** (incl. `SwipeRewindAcceptanceTest` 8/8 with out-of-order & duplicate-counter regression tests).
- `services/deck` (`./mvnw -B -Dtest=SwipeRewindDeckAcceptanceTest,DeckServiceTest test`) — **16/16 PASS** (incl. `SwipeRewindDeckAcceptanceTest` 4/4).
- `services/deck-read` (`../gateway/mvnw -B -Dtest=SwipeRewindDeckReadAcceptanceTest,DeckMaterializationServiceTest test`) — **11/11 PASS** (incl. `SwipeRewindDeckReadAcceptanceTest` 6/6 with Lua ordering & DLT serializer regression tests).
- `clients/tinder-client` (`npm test -- --watch=false && npm run build`) — **23/23 files, 112/112 tests PASS** (incl. `discover.rewind.acceptance.spec.ts` 12/12) + production build **PASS**.

_(Pre-implementation RED baseline from P3.5: all 6 implementation services failed for the exact missing behaviour before slices S1–S6 were coded.)_
