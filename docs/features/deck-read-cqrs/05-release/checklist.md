# Phase 5 release checklist

Status: **GO for engineering merge**. All repository-scoped contract, correctness, recovery and topology gates have evidence. Production readiness and deployed cutover are not evaluated by this feature because no production environment is defined in the repository.

## Passed

- [x] Contract validator parses OpenAPI 3.1 / AsyncAPI 2.6 and checks boundary vocabulary.
- [x] Profiles full-card LIVE events, photo version advancement and focused outbox/backfill behaviour tests.
- [x] Deck Read has no synchronous Profiles client or authoritative per-replica card/identity cache.
- [x] v2 HTTP contract covers `200/202/400/401`, `Retry-After`, invalid cursor/limit and generation reset; failure codes are executable policy checks.
- [x] v1 preserves a bare array and reads the local projection.
- [x] Duplicate/out-of-order profile events, duplicate swipe, immediate swipe/match exclusion and atomic snapshot Lua path are Redis-backed.
- [x] Native Gherkin-style acceptance is selectable through the `acceptance` tag; all FR/error rows link to executable Feature/Scenario/Given/When/Then checks.
- [x] Gateway routes v1/v2 and Angular builds against DeckPage/cursor polling.
- [x] Standalone development Redis is reproducible on `localhost:6380` and returned `PONG`; separate cluster topology parses.
- [x] `services/deck` has no feature diff.
- [x] Profiles development mode resolves PostgreSQL through Compose; existing volumes run one-shot V2 before Profiles starts, Hibernate validates only, and V1 → V2 → V2 replay passed as `profiles_app`.
- [x] Source `isSwiped` entries cannot enter fresh; successful empty sources become EMPTY only after the 30-second BUILDING window and do not count as failures.
- [x] Clean full suites pass for contracts (12), Deck Read (59), Profiles (292; 17 explicit opt-in/live-mTLS skips), Gateway (5), and Angular (25 plus production build).

## Runtime/infrastructure gates

- [x] Start the three-master topology and prove `cluster_state:ok`, three masters and all 16384 slots distributed.
- [x] Run viewer-local Lua install/lock operations against cluster mode and prove no CROSSSLOT.
- [x] Run duplicate Kafka delivery and sanitized DLT checks with real Kafka/Redis Testcontainers.
- [x] Run two Deck Read JVM replicas and prove all six partitions are shared and materialized once; separately prove shared-Redis build-token fencing keeps snapshot generations monotonic.
- [x] Run the recovery drill as linked producer/consumer evidence: PostgreSQL preserves and resumes the same runId/cursor, then real Kafka/Redis remains NOT_READY until duplicate-safe materialization, count and zero-lag checks complete.
- [x] Validate one-master failure, replica promotion, key availability and old-master rejoin on the isolated six-node topology.
- [x] Shadow-compare ordered profile IDs from v1 and v2 against the same local snapshot; retain the v1 adapter through the compatibility window.

## Future production scope — non-blocking for this merge

- [MIS-14: production Read Cluster backup/restore validation](https://linear.app/mischa8925/issue/MIS-14/future-scope-production-read-cluster-backuprestore-validation)
- [MIS-15: deployed backfill, Kafka catch-up and repeat recovery drill](https://linear.app/mischa8925/issue/MIS-15/future-scope-deployed-backfill-kafka-catch-up-and-repeat-recovery)
- [MIS-30: production rollout and v1/v2 traffic shadowing](https://linear.app/mischa8925/issue/MIS-30/future-scope-deck-read-production-rollout-and-v1v2-traffic-shadowing)

These checks require a deployment platform, production-like traffic and named operational ownership. They are deliberately not represented as failed engineering gates.

## Final rerun commands

```bash
ruby scripts/validate-deck-read-contracts.rb

cd services/tinder-contracts && mvn -B -DskipTests install
cd ../profiles && mvn -B clean test
cd ../deck-read && mvn -B clean test
cd ../deck-read && mvn -B -Dgroups=acceptance test
cd ../deck-read && mvn -B -DskipTests package
cd ../deck-read && mvn -B -DskipITs=false -Dit.test=DeckReadTwoReplicaIT failsafe:integration-test failsafe:verify
cd ../gateway && mvn -B clean test
cd ../../clients/tinder-client && npm test -- --watch=false && npm run build
```

`ComprehensiveIntegrationTest` is intentionally opt-in via `-Dprofiles.full-stack=true`; it requires the live Keycloak/Swipes/Deck/mTLS chain. Live mTLS probe tests are likewise reported as skipped until their external endpoints and certificates are supplied.

The automated recovery drill deliberately links two bounded integration tests rather than claiming a deployed mTLS exercise: `JpaDeckCardProjectionBackfillAdapterIntegrationTest` proves durable same-runId resume in Profiles PostgreSQL, while `DeckReadKafkaRedisRuntimeAcceptanceTest` proves the consuming Kafka/Redis sequence and manual readiness boundary. The deployed internal endpoint and certificate path remain part of the operational runbook.
