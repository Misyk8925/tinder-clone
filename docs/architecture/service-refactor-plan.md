# Service Refactor & Separation Plan

> **Status:** Proposal / design doc. No code has been changed by this document.
> **Goal:** (1) Refactor the "big" services so that **communication, API, logic, and domain**
> are cleanly separated, and (2) extract **deck-read** and **location** into their own
> independently-scalable services.
> **Scope decision recorded:** location is to be extracted as a **full standalone service**
> (entity + geocoding + PostGIS proximity), not just a geocoding proxy. The hot-path
> trade-offs of that choice and their mitigations are documented in [§5](#5-extract-a-standalone-location-service).

---

## 1. Current state (grounded)

The system is **already** a Spring Boot 3.x / Spring Cloud microservices platform
(config-server, Eureka, gateway + ~8 domain services, plus a Go swipe service). So this
work is **not** a monolith decomposition — it is (a) tidying the internals of the two
oversized services and (b) carving two more scaling seams out of them.

### 1.1 The two oversized services

| Service | ~LOC | Concerns it currently owns |
|---|---|---|
| **`profiles`** | ~6,400 | Profile CRUD · **deck read path** · **location + geocoding** · photos/S3 · preferences · hobbies · Keycloak user mgmt · Redis cache · Kafka outbox · gRPC server |
| **`deck`** | ~3,100 | Deck **build/score/cache** pipeline · **location proximity + distance math** · reactive Redis writer · Kafka consumers |

### 1.2 The "fat application service" smell

`services/profiles/.../profile/ProfileApplicationService.java` (478 LOC) is the clearest
example of mixed concerns in one class:

- **Infrastructure / communication** — `ResilientCacheManager` (Redis), `ProfileOutboxService` (Kafka)
- **Persistence** — `ProfileRepository` (JPA)
- **Domain orchestration** — `ProfileDomainService`, `detectChangedFields`, `determineChangeType`, `preferencesEqual`
- **Cross-domain coupling** — calls `LocationService.createFromCoordinates(...)` inline inside `update()` and `patch()`

Caching, persistence, event-building, change-classification, and location resolution all
live in the same method bodies. This is the prime target for layering.

### 1.3 The deck read path is glued to profile CRUD

The deck **builder** is already its own reactive service (`services/deck`: pipeline →
scoring → Redis). But the deck **read** path lives **inside `profiles`**:

- `profiles/.../deck/DeckCacheReader.java` (266 LOC of Redis ZSET reads + validity filtering)
- `profiles/.../deck/DeckService.java` (cache→ensure→fallback orchestration)
- `profiles/.../deck/DeckClient.java` (WebClient → deck builder `/internal/deck/ensure`)
- `profiles/.../profile/ProfileController.java` → `GET /api/v1/profiles/deck`

This is the **highest-QPS endpoint in the system** (called on every swipe session, with
read-ahead paging), and it is bound to the same JVM/deployment as profile writes.

### 1.4 Location logic is spread and duplicated

- `profiles/.../location/` — `Location` (PostGIS `geography(Point,4326)`), `LocationRepository`, `LocationService` (3-level cache + geocoding orchestration), `LocationDto`
- `profiles/.../geocoding/` — `NominatimService` (external OSM geocoder), `GeocodingConfig`
- `deck/.../service/scoring/LocationProximityStrategy.java` — **Haversine distance** + 0.8 score weight
- `deck/.../service/pipeline/util/LocationFilterUtil.java` — `isWithinRange(viewer, candidate, maxRange)`

So distance/proximity logic is **implemented twice** (conceptually) and the `Shared*Dto`
carrier types (`SharedProfileDto`, `SharedLocationDto`, `SharedPreferencesDto`) are
**physically copied** between `profiles` and `deck`.

### 1.5 Existing communication patterns (reuse these)

- **REST** — deck → profiles `/internal/search`, `/by-ids`, `/active`; subscriptions → profiles
- **gRPC** — subscriptions → profiles `UpdatePremiumUser` (mTLS, port 9010)
- **Kafka** — `profile.created/updated/deleted`, `swipe-created`, `match.created`
- **Outbox** — profiles & consumer use transactional outbox for reliable publish
- **Resilience4j** — circuit breakers/timeouts/retries on cross-service HTTP

---

## 2. Target architecture (overview)

```
                          ┌─────────────┐
                          │   Gateway   │
                          └──────┬──────┘
          ┌──────────────┬───────┼────────────┬───────────────┐
          ▼              ▼       ▼            ▼               ▼
   ┌────────────┐ ┌────────────┐ ┌──────────────┐ ┌──────────────┐
   │  profiles  │ │ deck-read  │ │   location   │ │ subscriptions│  ...
   │ (CRUD,     │ │ (CQRS read │ │ (entity +    │ │              │
   │  writes)   │ │  model)    │ │  geocode +   │ │              │
   └─────┬──────┘ └─────┬──────┘ │  PostGIS)    │ └──────────────┘
         │              │        └──────┬───────┘
         │ events       │ reads Redis   │
         ▼              ▼ + hydrates     │ owns geo DB
   ┌────────────┐ ┌────────────┐        │
   │   Kafka    │ │   deck     │◄───────┘ (proximity/geocode API + events)
   │            │ │ (builder)  │
   └────────────┘ └────────────┘
```

New/changed services:
- **`deck-read`** *(new)* — serves `GET .../deck`; reads the Redis deck and hydrates profiles. Scales independently of profile writes.
- **`location`** *(new)* — owns the `location` table, geocoding (Nominatim), and PostGIS proximity/distance. Removes the duplicated geo logic.
- **`profiles`** *(shrinks)* — pure profile/preferences/photos CRUD + write-side events.
- **`deck`** *(builder, unchanged role)* — keeps building/scoring, but uses the shared geo library locally for hot-path scoring and the location bulk API where a server-side proximity query is needed.
- **`tinder-contracts`** *(new shared lib)* — single home for cross-service DTOs and Kafka event schemas.

And cross-cutting: every service adopts the **same internal layering** (next section).

---

## 3. Internal layering: separate communication / API / logic / domain

Apply a **hexagonal (ports & adapters)** slice, organised **package-by-feature**. This is
the highest-value, lowest-risk change and should land first — it is a pure refactor with
no topology change.

### 3.1 Target package shape (per feature, e.g. `profile`)

```
com.tinder.profiles.profile
├── api/                      ← INBOUND adapters (driving side)
│   ├── ProfileController.java        (REST)
│   ├── ProfileGrpcService.java       (gRPC, if any)
│   └── dto/ …                        (request/response models, API-version-specific)
├── application/              ← USE CASES (orchestration only, no infra, no framework leakage)
│   ├── CreateProfileUseCase.java
│   ├── UpdateProfileUseCase.java
│   ├── PatchProfileUseCase.java
│   ├── GetProfileUseCase.java
│   └── port/
│       ├── in/  …            (use-case interfaces the API calls)
│       └── out/              ← PORTS the application needs (interfaces only)
│           ├── ProfileRepositoryPort.java
│           ├── ProfileCachePort.java
│           ├── DomainEventPublisherPort.java
│           └── LocationPort.java      (talk to location service via a port)
├── domain/                   ← PURE business model (no Spring, no JPA annotations ideally)
│   ├── Profile.java                  (entity / aggregate)
│   ├── ProfileDomainService.java     (rules: validate, sanitize-policy, canDelete)
│   ├── ChangeType.java + change-classification logic
│   └── event/ ProfileCreated, ProfileUpdated, ProfileDeleted
└── infrastructure/           ← OUTBOUND adapters (driven side)
    ├── persistence/  JpaProfileRepositoryAdapter.java, ProfileJpaEntity (if separated)
    ├── cache/        RedisProfileCacheAdapter.java   (wraps ResilientCacheManager)
    ├── messaging/    OutboxEventPublisherAdapter.java
    └── location/     LocationServiceClientAdapter.java (HTTP/gRPC → location service)
```

**Dependency rule:** `api → application → domain` and `infrastructure → application/domain`.
`domain` depends on nothing. `application` depends only on `domain` + its own ports.
Adapters implement ports; nothing in `domain`/`application` imports Spring Data, Redis,
Kafka, or WebClient types.

### 3.2 Worked example — decomposing `ProfileApplicationService`

| Today (one 478-LOC class) | Moves to |
|---|---|
| Redis get/put/evict (`PROFILE_CACHE_NAME`, `resilientCacheManager`) | `infrastructure/cache/RedisProfileCacheAdapter` behind `ProfileCachePort` |
| `profileOutboxService.enqueue*` | `infrastructure/messaging/OutboxEventPublisherAdapter` behind `DomainEventPublisherPort` |
| `locationService.createFromCoordinates/create` | `infrastructure/location/LocationServiceClientAdapter` behind `LocationPort` |
| `detectChangedFields`, `determineChangeType`, `preferencesEqual` | `domain` (pure functions / domain service) |
| `findByUserId`, `save` | `infrastructure/persistence/JpaProfileRepositoryAdapter` behind `ProfileRepositoryPort` |
| `create/update/patch/delete` orchestration | `application/*UseCase` (calls ports + domain, builds events) |

The use case becomes readable orchestration, e.g.:

```
UpdateProfileUseCase:
  profile      = repositoryPort.findByUserId(userId)        // port
  domain.validateAndSanitize(dto)                            // domain
  changed      = domain.detectChanges(profile, dto)          // domain
  profile.applyLocation(locationPort.resolve(dto))           // port
  repositoryPort.save(profile)                               // port
  cachePort.put(profile)                                     // port
  eventPublisherPort.publish(domain.classify(changed))       // port + domain
```

### 3.3 Enforcement (so the layering doesn't rot)

1. **Spring Modulith** — model each feature as a module; `@ApplicationModuleTest` and the
   module verification fail the build on illegal cross-module access. This also lets you
   **prove the seams in-process before extracting a service** (see phasing).
2. **ArchUnit** tests — codify the dependency rule:
   - `api` must not access `infrastructure`
   - `domain` must not depend on Spring/JPA/Redis/Kafka/WebClient packages
   - adapters must only be reached through ports
3. Run both in CI per service.

### 3.4 Apply order

`profiles` first (worst offender) → `deck` builder → `match` (`ConversationServiceImpl`
is 416 LOC) → others. Each is independent; no big-bang.

---

## 4. Extract a `deck-read` service (CQRS read model)

**Why:** the read path is the hottest endpoint and is currently coupled to profile writes.
Splitting it lets you scale read replicas and isolate Redis read load independently, and
shrinks `profiles` toward pure CRUD.

### 4.1 What moves out of `profiles`

- `deck/DeckCacheReader.java`, `deck/DeckService.java`, `deck/DeckClient.java`
- the `GET /api/v1/profiles/deck` handler (becomes `GET /api/v1/deck` in the new service)
- the `DeckEntryDto` record + Redis key constants (`deck:*`)

### 4.2 New service shape (`services/deck-read`, reactive WebFlux to match the builder)

```
deck-read/
├── api/            DeckController         GET /api/v1/deck?offset&limit  (JWT → viewerId)
├── application/    ReadDeckUseCase        (cache → ensure → fallback orchestration)
│   └── port/out/   DeckCachePort, DeckBuilderPort, ProfileHydrationPort
├── domain/         DeckPage, DeckEntry, validity rules (swiped/deleted/invalidated-at)
└── infrastructure/
    ├── cache/      RedisDeckCacheAdapter      (the ZSET read logic from DeckCacheReader)
    ├── builder/    DeckBuilderHttpAdapter     (→ deck builder /internal/deck/ensure)
    └── profiles/   ProfileHydrationAdapter    (→ profiles /by-ids or gRPC, batch hydrate)
```

### 4.3 Data ownership & contracts

- **Redis is shared by contract**: `deck` (builder) writes the ZSETs; `deck-read` only
  reads them. Freeze the complete key schema (`deck:{id}`, `deck:build:ts:{id}`,
  `deck:stale:{viewerId}`, `deck:lock:{viewerId}`, `deck:recent:viewers`,
  `deck:profile:deleted`, `deck:profile:invalidated-at:{id}`, and
  `prefs:{minAge}:{maxAge}:{gender}`) in `tinder-contracts` so the two services
  can't drift. This is the one place we intentionally share a datastore — it is
  the read-model handoff, and both sides are owned by the deck domain.
- **Profile hydration**: `deck-read` calls `profiles` `/by-ids` (already exists) or gRPC.
  Prefer **gRPC** for this hot batch lookup; add a small local cache + Resilience4j.
- The on-the-fly emergency fallback (`buildDeckOnTheFly` → `internalProfileService.searchByViewerPrefs`)
  should call the **deck builder** synchronously rather than reaching into profiles' internals.

### 4.4 Routing / migration

1. Stand up `deck-read`, route `GET /api/v1/deck` to it at the gateway.
2. Keep the old `/api/v1/profiles/deck` as a thin proxy/redirect for one release (strangler).
3. Update the Angular client to the new path; then delete the old handler + the `deck`
   package from `profiles`.

---

## 5. Extract a standalone `location` service

> **Recorded decision:** full standalone service (entity + geocoding + PostGIS proximity).
> The honest trade-off and the mitigations are below — they are part of the design, not a
> reason to skip the choice.

### 5.1 Responsibilities (consolidate everything geo into one owner)

- Own the **`location` table** and its PostGIS `geography(Point,4326)` data.
- **Geocoding**: city → coordinates via Nominatim (move `NominatimService` + `GeocodingConfig` here), with the existing L1/L2 cache + per-city lock behaviour preserved.
- **Proximity & distance**: replace the duplicated `LocationProximityStrategy` (Haversine) and `LocationFilterUtil` with a single authoritative implementation, ideally **pushed into PostGIS** (`ST_DWithin`, `ST_Distance`) instead of in-JVM Haversine.
- Expose APIs for: resolve-or-create location (by city / by coords), and **bulk** proximity filter + distance for a viewer against many candidates.

### 5.2 Service shape (`services/location`)

```
location/
├── api/
│   ├── LocationController          POST /resolve            (city|coords → locationId+coords)
│   │                                POST /proximity/filter   (viewer + candidateIds → within-range subset + distances)
│   └── LocationGrpcService          (gRPC variant of the above for hot callers)
├── application/   ResolveLocationUseCase, ProximityQueryUseCase
│   └── port/out/  LocationRepositoryPort, GeocoderPort
├── domain/        Location, GeoPoint, DistancePolicy (range/score rules)
└── infrastructure/
    ├── persistence/  PostgisLocationRepositoryAdapter (ST_DWithin / ST_Distance)
    └── geocoding/    NominatimGeocoderAdapter (+ resilience, rate-limit, cache)
```

### 5.3 The hot-path problem (and how this design avoids it)

Two call sites are latency-sensitive and must **not** gain a per-item network round-trip:

1. **Profile write path** (`create/update/patch` in `profiles`) resolves a location.
   - *Mitigation:* it's already a low-QPS, user-driven write. A single `POST /resolve`
     call per write is acceptable. Make `LocationPort` resilient (timeout + circuit
     breaker); on geocoder failure keep today's fallback (store coords / default point).

2. **Deck builder scoring loop** filters/scores ~2,000 candidates by distance.
   - *This is the dangerous one.* Calling `location` per candidate would be fatal.
   - *Mitigations (do all three):*
     - **Denormalize coordinates** onto the candidate data the builder already fetches
       (the profile `/internal/search` payload already carries `SharedLocationDto` with
       lat/lon). The builder computes distance **locally** from those coords using the
       **shared geo library** (see §5.4) — no call to `location` in the loop.
     - For correctness-critical or large queries, expose a **single bulk**
       `POST /proximity/filter` that runs **one** `ST_DWithin` query server-side and
       returns the in-range subset — one round-trip, not N.
     - The authoritative geo math lives in **one** place (library + PostGIS), so
       "denormalized local compute" and "server-side query" can't disagree.

### 5.4 Avoiding re-duplication: a thin `geo` library

Extract the pure distance/score functions (Haversine + the `(1 - d/maxRange)*0.8` score)
into a tiny shared library (part of `tinder-contracts` or a sibling `tinder-geo`). The
`location` service and the `deck` builder both depend on it. This removes the current
double implementation while still allowing the builder to compute locally in its tight loop.

### 5.5 Data migration

- Move the `location` table to the location service's schema/DB.
- `profiles.profile.location_id` currently FKs `location`. Across a service boundary you
  **cannot keep a hard FK**. Options (pick per §8 decision):
  - **(a)** Keep `location_id` as a soft reference on profile + **denormalize lat/lon/city**
    onto the profile row (so reads/search don't need a join or a call). location service is
    the system of record; profiles caches the coords it was given. *(Recommended.)*
  - **(b)** Profiles stores only `location_id` and always calls location to read coords
    (simpler data, more coupling on reads — worse for search/deck).
- Migration is a Flyway change set + a backfill job; do it behind the strangler so the old
  in-profiles `location` package keeps working until cutover.

---

## 6. Shared contracts module (`tinder-contracts`)

Create one Maven library, depended on by all services, owning:

- Cross-service DTOs currently **copied**: `SharedProfileDto`, `SharedLocationDto`, `SharedPreferencesDto`, `SharedPhotoDto`, `SharedSwipeRecordDto`, `DeckEntry`.
- **Kafka event schemas**: `ProfileCreated/Updated/Deleted`, `ChangeType`, `SwipeCreated`, `MatchCreated` — **versioned** (e.g. `v1` packages) so producers/consumers evolve safely.
- The frozen **Redis deck key schema** constants (shared by `deck` and `deck-read`).
- Optionally the `tinder-geo` distance/score functions (§5.4).

Pair this with **consumer-driven contract tests** (e.g. Spring Cloud Contract) between each
caller/callee pair so an API change breaks CI, not production.

---

## 7. Phased migration (strangler-fig, each phase shippable)

| Phase | Deliverable | Risk | Reversible? |
|---|---|---|---|
| **0** | `tinder-contracts` lib; de-duplicate `Shared*Dto` + event schemas; version events | Low | Yes |
| **1** | Internal layering of `profiles` (api/application/domain/infrastructure) + Spring Modulith + ArchUnit | Low | Yes (pure refactor) |
| **2** | Same layering for `deck` builder & `match` | Low | Yes |
| **3** | Extract **`deck-read`**; gateway routes `/api/v1/deck`; old path proxied then removed | Medium | Yes (re-point gateway) |
| **4** | Extract **`location`** (geocoding + PostGIS); introduce `tinder-geo`; denormalize coords; deck builder uses library/bulk query | Medium-High | Harder (data move) |
| **5** | Remove dead `deck`/`location`/`geocoding` packages from `profiles`; delete old routes | Low | n/a |

Guiding principles throughout:
- **Keep the public API stable behind the gateway** — clients shouldn't notice phases 1–2.
- Each extraction goes **route-by-route**: run new + old in parallel, shadow/redirect, then delete.
- No phase requires the next; stop after any phase with a coherent system.

---

## 8. Open decisions to confirm before building

1. **Location DB reference strategy** — §5.5 option (a) denormalize coords *(recommended)* vs (b) store only `location_id`.
2. **deck-read ↔ profiles hydration transport** — gRPC *(recommended for the hot batch)* vs reuse REST `/by-ids`.
3. **Geo math home** — shared `tinder-geo` lib + PostGIS *(recommended)* vs location-service-only (forces bulk calls from the builder).
4. **Event versioning approach** — version-in-package (`...event.v1`) vs schema registry (Avro/Protobuf). The latter is more work but stronger for a multi-consumer Kafka topology.
5. **Spring Modulith adoption** — adopt as the enforcement mechanism in phase 1, or rely on ArchUnit alone.

---

## 9. Risks & mitigations (summary)

| Risk | Mitigation |
|---|---|
| Per-item network calls in deck scoring loop | Denormalize coords + single bulk proximity query + shared geo lib (§5.3) |
| Cross-service FK (`profile.location_id`) | Soft reference + denormalized coords; Flyway move + backfill behind strangler (§5.5) |
| Redis deck schema drift between builder & reader | Freeze key schema in `tinder-contracts`; both sides depend on it (§4.3) |
| DTO/event drift across services | Single contracts lib + consumer-driven contract tests (§6) |
| Layering erosion over time | Spring Modulith verification + ArchUnit in CI (§3.3) |
| Added latency on profile writes (location call) | Resilient `LocationPort` (timeout/CB) + existing fallback behaviour (§5.3) |
| Big-bang regression | Strangler-fig, route-by-route, every phase independently shippable (§7) |

---

## 10. Testing strategy

- **Unit** — domain + use cases become trivially unit-testable once infra is behind ports (no Spring context needed).
- **ArchUnit / Modulith** — structural tests guard the layering and module boundaries.
- **Integration** — Testcontainers (PostgreSQL+PostGIS, Redis) per service; EmbeddedKafka for event flows; `StepVerifier` for reactive `deck-read`/`location`.
- **Contract** — consumer-driven contracts for: deck-read→profiles, deck-builder→location, profiles→location.
- **Migration safety** — for phase 4, run a parallel-read comparison (old in-profiles location vs new service) before cutover.
