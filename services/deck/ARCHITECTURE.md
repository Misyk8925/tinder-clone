# Deck Service Architecture

## Scope
This document describes the architecture of the `services/deck` service based on a review of all Java source files in:

- `services/deck/src/main/java` (42 files)
- `services/deck/src/test/java` (18 files)

It is organized top-down: system role, execution flows, folder structure, and code-level patterns.

---

## 1) High-Level Architecture

### Service role
`deck` is a reactive Spring Boot service that builds and maintains ranked candidate decks per viewer.

It acts as a cache-centric orchestration service:

- pulls candidate profiles from Profiles service
- filters already-swiped candidates using Swipes/Consumer service
- scores and ranks candidates
- writes ranked deck into Redis
- keeps decks fresh via scheduler and Kafka-driven invalidation/removal

### Architecture style
The service combines:

- **Pipeline orchestration** for synchronous deck rebuild (`DeckPipeline`)
- **Event-driven cache maintenance** through Kafka consumers
- **Redis as read-optimized deck store**
- **Reactive APIs (Project Reactor)** with selective imperative bridges (`block()`, `subscribe()`)

### External dependencies

- **Profiles service (HTTP)**: candidate search, active users, profile fetch, by-ids fetch
- **Swipes/Consumer service (HTTP)**: batch swipe-history check
- **Redis**: deck data, timestamps, stale markers, rebuild locks, preferences cache
- **Kafka**: profile update/delete events, swipe created events

---

## 2) Runtime Component View

### Core components

- `DeckController`: admin and manual endpoints
- `DeckScheduler`: periodic background rebuild trigger
- `DeckService`: per-viewer rebuild orchestration entrypoint
- `DeckPipeline`: staged flow (search -> swipe filter -> scoring -> cache)
- `DeckCache`: Redis access and cache primitives
- `ProfileEventConsumer` / `SwipeEventConsumer`: Kafka event handlers
- `DeckResilience`: resilience layer (timeout + retry + circuit breaker + bulkhead)

### Data stores and key spaces
Redis keys used by `DeckCache`:

- `deck:{viewerId}` -> ZSET of candidate UUID strings scored by rank value
- `deck:build:ts:{viewerId}` -> last build timestamp (epoch millis)
- `deck:stale:{viewerId}` -> SET of stale candidate UUIDs
- `deck:lock:{viewerId}` -> rebuild lock key
- `prefs:{minAge}:{maxAge}:{gender}` -> SET of candidate UUIDs for shared preferences cache

---

## 3) Main Execution Flows

### 3.1 Deck Build Flow (pipeline)

Entry points:

- scheduled batch (`DeckScheduler.rebuildAllDecks`)
- manual rebuild endpoint (`DeckController.rebuild`)
- direct per-user call (`DeckService.rebuildOneDeck`)

Pipeline steps in `DeckPipeline.buildDeck(viewer)`:

1. `CandidateSearchStage.searchCandidates(viewer)`
2. `SwipeFilterStage.filterBySwipeHistory(viewer, candidates)`
3. `ScoringStage.scoreAndRank(viewer, filtered)`
4. enforce `deck.per-user-limit`
5. `CacheStage.cacheDeck(viewerId, scoredCandidates)` -> Redis write

If final list is empty, cache write is skipped.

### 3.2 Candidate Search Flow

`CandidateSearchStage` supports two modes:

- **Direct mode** (default): calls `ProfilesHttp.searchProfiles(...)`
- **Preferences-cache mode** (`deck.preferences-cache-enabled=true`):
  - check `DeckCache.hasPreferencesCache(...)`
  - on hit: load candidate IDs from Redis set, fetch full profiles via `/by-ids`
  - on miss: query profiles service, then async-cache candidate IDs
  - apply location filter (`LocationFilterUtil`) on returned full profiles

### 3.3 Swipe History Filtering

`SwipeFilterStage`:

- buffers candidates into batches (`batchSize`, default configured via `BasicStage`)
- calls `SwipesHttp.betweenBatch(viewerId, candidateIds)`
- removes candidates where map says swipe exists
- applies timeout + retry
- **fail-open behavior**: on swipes error, returns unfiltered batch

### 3.4 Scoring and Ranking

`ScoringStage`:

- parallel scoring (`parallel(parallelism)` + `Schedulers.parallel()`)
- delegates score computation to `ScoringService`
- sorts descending by score
- emits `ScoredCandidate(candidateId, score)`

`ScoringService` aggregates all injected `ScoringStrategy` beans:

- `AgeCompatibilityStrategy` (weight 1.0)
- `LocationProximityStrategy` (weight 0.8, Haversine distance)

### 3.5 Cache Write Flow

`CacheStage` converts scored stream to list of `(candidateId, score)` and calls:

- `DeckCache.writeDeck(viewerId, deckEntries, ttl)`

`writeDeck` behavior:

- delete existing deck + timestamp keys
- `ZADD` all candidates with scores
- set deck TTL
- persist build timestamp key

### 3.6 Scheduler Flow

`DeckScheduler.rebuildAllDecks()`:

- fetch active users from Profiles (`/active`)
- for each user call `rebuildDeckForUser`
- each user rebuild runs reactive flow with timeout and own subscription

Scheduling uses:

- `@Scheduled(cron = "${deck.scheduler.cron:0 0/1 * * * *}")`

### 3.7 Kafka Event Flows

### Profile events (`ProfileEventConsumer`)

Consumes:

- `${kafka.topics.profile-events}` as `ProfileUpdateEvent`
- `${kafka.topics.delete-events}` as `ProfileDeleteEvent`

`ProfileUpdateEvent` handling:

- `PREFERENCES`: invalidate personal deck only
- `CRITICAL_FIELDS`: mark profile stale across all cached decks
- `LOCATION_CHANGE`: invalidate personal deck + mark stale across decks
- `NON_CRITICAL`: no cache mutation

`ProfileDeleteEvent` handling:

- remove deleted profile from all decks
- mark deleted profile stale across decks
- invalidate deleted profile personal deck

### Swipe events (`SwipeEventConsumer`)

Consumes `${kafka.topics.swipe-events}` as `SwipeCreatedEvent`:

- parse `profile1Id` (swiper) and `profile2Id` (swiped)
- remove swiped profile from swiper deck

---

## 4) Package and Folder Structure

Top package: `com.tinder.deck`

### `adapters/`
HTTP integrations with other services.

- `ProfilesHttp`
- `SwipesHttp`

### `auth/`
Outbound auth helper.

- `KeycloakTokenClient` (client credentials token fetch + in-memory token cache)

### `config/`
Spring configuration and infrastructure beans.

- `AsyncConfig`
- `DeckResilienceConfig`
- `DeckResilienceProperties`
- `HttpClientConfig`
- `RedisConfig`
- `SchedulingConfig`

### `controlller/`
Administrative REST endpoints (note package name includes triple `l`).

- `DeckController`

### `dto/`
Shared transport types.

- `SharedLocationDto`
- `SharedPhotoDto`
- `SharedPreferencesDto`
- `SharedProfileDto`
- `SharedSwipeRecordDto`
- `SwipeRecordId`

### `kafka/config/`
Kafka consumer/producer and error-handler configuration.

- `KafkaConsumerConfig`
- `KafkaProducerConfig`

### `kafka/consumer/`
Kafka message handlers.

- `ProfileEventConsumer`
- `SwipeEventConsumer`

### `kafka/dto/`
Event payload models.

- `ChangeType`
- `ProfileDeleteEvent`
- `ProfileUpdateEvent`
- `SwipeCreatedEvent`

### `resilience/`
Resilience4j policy composition and reactive wrapping.

- `DeckResilience`

### `service/`
Application services and Redis cache abstraction.

- `DeckCache`
- `DeckScheduler`
- `DeckService`
- `ScoringService`

### `service/pipeline/`
Deck build stages and orchestration.

- `BasicStage`
- `CandidateSearchStage`
- `SwipeFilterStage`
- `ScoringStage`
- `CacheStage`
- `DeckPipeline`

### `service/pipeline/util/`
Pipeline helper utilities.

- `LocationFilterUtil`
- `PreferencesCacheHelper`
- `PreferencesUtil`

### `service/scoring/`
Scoring strategy abstraction and concrete strategies.

- `ScoringStrategy`
- `AgeCompatibilityStrategy`
- `LocationProximityStrategy`

---

## 5) Key Patterns Used

### 5.1 Pipeline pattern
`DeckPipeline` composes stages as a linear transformation chain. Each stage has one responsibility and can be tested independently.

### 5.2 Strategy pattern
`ScoringService` uses injected `List<ScoringStrategy>` to compose final score without hard-coding specific dimensions.

### 5.3 Adapter pattern
`ProfilesHttp` and `SwipesHttp` isolate external HTTP details from pipeline logic.

### 5.4 Event-driven cache consistency
Kafka consumers mutate cache on profile/swipe events, reducing stale data windows without full rebuilds.

### 5.5 Cache-as-read-model
Deck is materialized in Redis ZSET for low-latency reads and sorted retrieval.

### 5.6 Distributed lock pattern
`DeckCache.acquireLock` uses Redis `SETNX + TTL` to avoid duplicate concurrent rebuild work per viewer.

### 5.7 Stale-marker pattern
Instead of immediate expensive full-deck rewrites for all viewers, profile changes can mark entries stale and filter them at read time.

### 5.8 Resilience policy composition
`DeckResilience` applies timeout, retry, circuit breaker, and bulkhead via Reactor operators.

### 5.9 Fail-open behavior for non-critical dependency
Swipe history failures return empty map so deck generation can continue.

---

## 6) Reactive and Concurrency Model

### Reactive usage

- Service and adapter layers mostly use `Mono`/`Flux`
- Pipeline stages are reactive transformations
- Redis operations are reactive (`ReactiveStringRedisTemplate`)

### Parallelism

- scoring stage parallelizes scoring across candidates
- Kafka listeners use configurable concurrency
- scheduler triggers independent user rebuild subscriptions

### Imperative bridges
Some components intentionally block/subscribe:

- Kafka consumers use `.block()` to finish processing before listener returns
- scheduler uses `.subscribe()` to kick off background processing
- lock cleanup in `withLock` uses `doFinally(...subscribe())`

This creates a hybrid reactive/imperative runtime model.

---

## 7) Configuration Surface

Primary runtime config is in `src/main/resources/application.yml`.

Key groups:

- `profiles.base-url`, `swipes.base-url`
- `deck.*` (parallelism, timeout, retries, cache TTL, limits)
- `deck.preferences-cache-*`
- `deck.rebuild.*` (queue/lock/stale settings)
- `deck.scheduler.*` (cron, concurrency cap)
- `deck.resilience.*` via `DeckResilienceProperties`
- `spring.kafka.*` and `kafka.topics.*`
- `management.*` for actuator/prometheus

Test overrides are in `src/test/resources/application-test.yml`.

---

## 8) Testing Architecture

Test suite shape:

- **Unit tests** for controller, service, pipeline stages, Kafka consumers
- **Integration tests with Testcontainers** for Redis and Kafka scenarios
- **Pipeline integration tests** combining real Redis + mocked HTTP adapters

Coverage focus:

- deck cache operations (ordering, pagination, invalidation, stale/lock features)
- full pipeline behavior and limits
- Kafka-driven cache update semantics
- scheduler-triggered rebuild behavior

---

## 9) Notable Implementation Characteristics

- `ProfilesHttp` is wrapped with `DeckResilience`; `SwipesHttp` currently is not.
- Both `DeckApplication` and `SchedulingConfig` enable scheduling.
- Controller package path is `controlller` (triple `l`).
- Preferences cache invalidation is mostly TTL-based for critical field updates.
- `KeycloakTokenClient` exists as utility but is not currently wired into `HttpClientConfig`.

---

## 10) End-to-End Summary

At runtime, Deck service continuously materializes per-user ranked candidate lists in Redis by:

1. searching profile candidates
2. filtering by swipe history
3. scoring and ranking
4. caching with TTL and timestamp

It keeps cache correctness through:

- periodic scheduled rebuilds
- Kafka-driven incremental invalidation/removal
- stale markers and deck-level locking

This results in a pragmatic architecture optimized for fast deck reads and manageable rebuild cost under frequent profile/swipe updates.
