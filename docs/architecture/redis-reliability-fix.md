# Redis reliability fix evidence

Date: 2026-08-14

## Scope and severity

This bug-fix slice covers the shared Redis Compose policy and Deck's Redis correctness paths.
The confirmed defects were major because they could lose cached coordination state after a
restart, evict correctness-related keys under memory pressure, make Redis latency proportional
to the full keyspace, or corrupt lock ownership during a lease race.

## Reproduced defects

The following regression checks failed before the implementation changes:

- `ruby scripts/validate-redis-config.rb`: five failures for missing AOF, missing `everysec`
  fsync, `allkeys-lru`, missing `/data` mount, and missing named volume.
- `DeckServiceTest#testEnsureDeckRecordsRecentViewer`: `ensureDeck` did not record activity,
  so the recent-viewer scheduler could omit active users.
- `DeckCacheReverseIndexIntegrationTest#markAsStaleForAllDecksUsesAffectedReverseIndexOnly`:
  the implementation scanned and marked unrelated deck keys.
- `DeckCachePhase2IntegrationTest#staleOwnerMustNotDeleteSuccessorLock`: a stale owner deleted
  a successor's lock.
- `DeckCacheIntegrationTest#getRecentViewerIdsPrunesExpiredMembers`: expired activity entries
  accumulated in the ZSET.
- `DeckCacheReverseIndexIntegrationTest#rewriteRemovesObsoleteReverseMemberships`: rewritten
  decks left obsolete reverse-index memberships.
- `DeckCachePhase2IntegrationTest#shouldUseConfiguredLockTimeout`: the runtime lease remained
  approximately 30 seconds when the test configured 3 seconds.
- `DeckCacheReverseIndexIntegrationTest#removeFromDeckCleansReverseMembership` and
  `#removeMultipleFromDeckCleansReverseMemberships`: swipe removals left reverse memberships.
- `ruby scripts/validate-redis-config.rb`: the Deck container used the ignored
  `SPRING_REDIS_TIMEOUT` name and had no effective command/connect timeout configuration.

## Root causes and corrections

- Compose used an eviction-only, non-persistent Redis configuration. It now uses AOF with
  `appendfsync everysec`, a persistent volume, and `noeviction` so memory exhaustion is explicit.
- Deck activity was read but never recorded on the `ensureDeck` path. The path now touches the
  recent-viewer ZSET and reads prune expired entries.
- Profile fan-out used a blocking keyspace scan. It now uses `deck:contains:{profileId}` and
  maintains that reverse index on writes, rewrites, invalidation, single removal, and batch removal.
- Locks used a shared value and unconditional asynchronous delete. They now use unique tokens,
  atomic compare-and-delete, awaited lifecycle cleanup, and the configured lease duration.
- Deck now binds explicit two-second Redis command and connect timeouts through Spring Data's
  supported property names; both remain environment-overridable.

## Verification and operational boundary

The focused regressions and the affected Deck suites run against Testcontainers Redis. The
Compose validator checks the declared durability and eviction policy without starting the full
stack.

Passed checks:

- focused lock-timeout and reverse-index cleanup regressions: 3 tests, 0 failures
- affected Deck cache/service/scheduler/Kafka-consumer suites: 86 tests, 0 failures
- `ruby scripts/validate-redis-config.rb`: `Redis Compose policy: OK`
- `docker compose config --no-interpolate --quiet`: valid Compose model
- no `redis.keys(...)` call remains in Deck production code
- `platform/redis-policy-dsl`: typed desired state and catalog-to-runtime drift checks

This does not prove Redis node failover, restore from AOF on deployed storage, or behavior under
memory exhaustion. The current topology remains a single Redis node without in-project TLS/auth
configuration; production HA and secret management require a deployment decision. Deck rewrite
and reverse-index maintenance also remain a recoverable multi-command operation rather than one
crash-atomic Redis transaction.

Rollback is a normal revert of the Deck, tests, Compose Redis stanza, and validator changes. A
created `redis-data` volume should be retained during rollback unless its contents are explicitly
confirmed disposable.
