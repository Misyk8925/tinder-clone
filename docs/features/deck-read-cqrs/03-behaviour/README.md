# Behaviour traceability — deck-read-cqrs

Acceptance format: project-native JUnit 5 / Quarkus HTTP / Angular Vitest tests. Every acceptance class/spec is named as a `Feature`, every check as a `Scenario: Given … when … then …`, and test bodies expose explicit Given/When/Then sections. Java scenarios carry the `acceptance` tag. This follows the repository's native BDD convention; no Cucumber dependency or non-executable duplicate `.feature` file is added.

Current status: **engineering delivery complete; Phase 5 evidence passed, checked 2026-08-13**. Contract, HTTP, projection, read-model, Gateway and Angular checks are green. Repository-local Redis Cluster failover and recovery evidence is complete; deployed production validation is explicitly future scope.

## FR / contract-error → executable acceptance check

| FR or error | Executable check | File |
|---|---|---|
| FR-1 | `unauthenticatedViewerIsRejected`, `malformedCursorIsRejectedAsProblemDetails`, `limitAboveOneHundredIsRejected`, `v2InitialBuildAdvertisesTwoSecondPollingWhenReadModelIsAvailable`, `v2CursorResetsToStartWhenSnapshotGenerationChanges`; `generationAndCursorResetAreFirstClassResponseBehaviour` | `DeckV2ContractAcceptanceTest`, `DeckResourceTest`, `DeckReadPolicyAcceptanceTest` |
| FR-2 | `readPathDoesNotDependOnSynchronousProfilesOrPerReplicaAuthoritativeCaches`; existing `DeckResourceTest` guards the v1 bare-array HTTP shape | `DeckReadCqrsBoundaryAcceptanceTest`, `DeckResourceTest` |
| FR-3 / NFR-7 | `sharedVersionedDeckCardEventExists`, `photoMutationsParticipateInTheTransactionalProjectionOutbox`, `restartablePagedBackfillEntryPointExists`, `backfillCheckpointIsDurableInProfilesPostgres`, `eachPageUsesFiveHundredRowsAndTheExistingTransactionalOutbox` | `ProfileDeckCardProjectionBoundaryAcceptanceTest` |
| FR-4 | `Given a newer card version, when older or conflicting same-version events arrive, then card and identity never roll back`; `Given a partial cross-slot identity write, when the exact event is redelivered, then the missing user mapping is repaired` | `DeckQueryServiceIntegrationTest` |
| FR-5 | `deckEnsureRemainsAndSourceRedisIsReadOnly`, `existingDeckServiceDoesNotOwnDeckReadKeys`, `jwtUserIdentityIsMappedLocallyBeforeProfileKeyedDeckAccess`; `Given a source entry already marked as swiped, when source ordering is imported, then it cannot enter fresh candidates` | `DeckReadCqrsBoundaryAcceptanceTest`, `DeckRedisReaderTest` |
| FR-6 | Eight component scenarios: successful fresh import; successful empty source stays BUILDING before 30s and becomes EMPTY after 30s without a failure; first/second/30-second failure thresholds; independent repeat readiness; unavailable result when no safe repeat exists | `DeckSnapshotBuilderAcceptanceTest` |
| FR-7 | Source `isSwiped` exclusion; `Given a fresh card, when the viewer swipes it, then it disappears immediately`; `Given a repeat card, when the profiles match, then it is excluded`; delete/event boundary scenario | `DeckRedisReaderTest`, `DeckQueryServiceIntegrationTest`, `DeckReadCqrsBoundaryAcceptanceTest` |
| FR-8 | Scenario outline: `Given the Read Cluster is unavailable, when v1/v2 is requested, then READ_MODEL_NOT_READY is returned`; unavailable-snapshot scenario | `DeckReadinessContractAcceptanceTest`, `DeckResourceTest` |
| FR-9 | `gatewayRoutesBothDeckVersionsToDeckReadWithoutRewrite`, `securityForwardsBothVersionsForDeckReadJwtValidation`, `requests a DeckPage from /api/v2/deck without the v1 offset parameter`; generation-aware dedup/current-card retention and 2s/30s/10s polling | `DeckReadV2RouteAcceptanceTest`, `profile.service.deck-v2.acceptance.spec.ts`, `discover.deck-v2.acceptance.spec.ts` |
| 202 BUILDING | `v2InitialBuildAdvertisesTwoSecondPollingWhenReadModelIsAvailable` | `DeckResourceTest` |
| 400 INVALID_CURSOR | `malformedCursorIsRejectedAsProblemDetails` | `DeckV2ContractAcceptanceTest` |
| 400 INVALID_LIMIT | `limitAboveOneHundredIsRejected` | `DeckV2ContractAcceptanceTest` |
| 400 INVALID_PAGINATION | `Given invalid v1 pagination, when the deck is requested, then INVALID_PAGINATION is returned` | `DeckResourceTest` |
| 401 UNAUTHENTICATED | `unauthenticatedViewerIsRejected` | `DeckV2ContractAcceptanceTest` |
| 503 READ_MODEL_NOT_READY | v1/v2 unavailable Read Cluster scenario outline | `DeckReadinessContractAcceptanceTest` |
| 503 DECK_TEMPORARILY_UNAVAILABLE | `Given a snapshot with no fresh or safely repeatable cards, when v2 is requested, then DECK_TEMPORARILY_UNAVAILABLE is returned` | `DeckResourceTest` |

## Exact locations

- `services/deck-read/src/test/java/com/tinder/deckread/architecture/DeckReadCqrsBoundaryAcceptanceTest.java`
- `services/deck-read/src/test/java/com/tinder/deckread/resource/DeckV2ContractAcceptanceTest.java`
- `services/deck-read/src/test/java/com/tinder/deckread/resource/DeckReadinessContractAcceptanceTest.java`
- `services/deck-read/src/test/java/com/tinder/deckread/redis/DeckRedisReaderTest.java`
- `services/deck-read/src/test/java/com/tinder/deckread/service/DeckReadPolicyAcceptanceTest.java`
- `services/deck-read/src/test/java/com/tinder/deckread/service/DeckSnapshotBuilderAcceptanceTest.java`
- `services/profiles/src/test/java/com/tinder/profiles/application/photos/ProfileDeckCardProjectionBoundaryAcceptanceTest.java`
- `services/gateway/src/test/java/com/tinder/gateway/DeckReadV2RouteAcceptanceTest.java`
- `clients/tinder-client/src/app/core/services/profile.service.deck-v2.acceptance.spec.ts`
- `clients/tinder-client/src/app/features/discover/discover.deck-v2.acceptance.spec.ts`

## Validation commands

```bash
ruby scripts/validate-deck-read-contracts.rb

cd services/deck-read
mvn -B -Dgroups=acceptance test

cd ../profiles
./mvnw -B -Dtest=ProfileDeckCardProjectionBoundaryAcceptanceTest test

cd ../gateway
./mvnw -B -Dtest=DeckReadV2RouteAcceptanceTest test

cd ../../clients/tinder-client
npm test -- --watch=false --include=src/app/core/services/profile.service.deck-v2.acceptance.spec.ts
npm test -- --watch=false --include=src/app/features/discover/discover.deck-v2.acceptance.spec.ts
```

## Combined validation notes

- `ruby scripts/validate-deck-read-contracts.rb` — **PASS** for the final OpenAPI, AsyncAPI, backfill and boundary contracts.
- `tinder-contracts: mvn -B -DskipTests install` — **PASS** after granting local `~/.m2` write access.
- Deck Read native Given/When/Then acceptance — **41/41 PASS**.
- Full Deck Read Maven suite — **59/59 PASS**, including real Kafka duplicate/DLT/recovery paths, source `isSwiped` exclusion, successful empty-source BUILDING/EMPTY semantics, RFC 7807, `Retry-After: 2`, exact v1 shape and v1/v2 ordered-ID parity, lost-cluster 503, version fencing, fresh/repeat thresholds, delete tombstones, immediate exclusions and generation reset.
- Selectable `acceptance` slice — **51/51 PASS**; two real Deck Read JVM replicas Failsafe IT — **1/1 PASS**.
- Full Profiles Maven suite — **292 tests, 0 failures, 0 errors, 17 explicit opt-in/live-mTLS skips** with PostgreSQL/PostGIS, Kafka and Redis Testcontainers.
- Gateway v1/v2 route checks — **4/4 PASS**.
- Angular v2 service/component acceptance — **4/4 PASS**; production build **PASS**.
- Compose syntax for the standalone three-master topology and the main six-node production-like topology — **PASS**.
- The exact commands, environment and remaining recovery/topology evidence are maintained in [`../04-implementation/log.md`](../04-implementation/log.md) and [`../05-release/checklist.md`](../05-release/checklist.md).
