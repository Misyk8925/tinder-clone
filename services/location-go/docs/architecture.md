# location-go — architecture

## Component overview

```mermaid
graph TD
    HTTP["HTTP Server\n:8065"]
    H["Handler\nhandler.go"]
    S["LocationService\nservice.go"]
    C["L1 Cache\nsync.Map"]
    SF["singleflight.Group\nper-city dedup"]
    R["Repository\nrepository.go"]
    G["Geocoder\ngeocoder.go"]
    CB["Circuit Breaker\nsonY/gobreaker"]
    DB[("location_db\nPostGIS")]
    NOM["Nominatim\nnominatim.openstreetmap.org"]

    HTTP --> H
    H --> S
    S --> C
    S --> SF
    SF --> R
    SF --> G
    R --> DB
    G --> CB
    CB --> NOM
```

---

## Resolve request — cache flow

A city that is already known never reaches the geocoder.
The `singleflight.Group` ensures concurrent requests for the same unknown city
share one geocoder + DB round-trip (replaces the Java `ReentrantLock` map).

```mermaid
sequenceDiagram
    participant Caller as Caller<br/>(profiles)
    participant H as Handler
    participant S as LocationService
    participant L1 as L1 Cache<br/>(sync.Map)
    participant SF as singleflight
    participant R as Repository
    participant DB as location_db
    participant G as Geocoder
    participant N as Nominatim

    Caller->>H: POST /api/v1/locations/resolve<br/>{"city":"Berlin"}
    H->>S: Resolve(ctx, "Berlin")
    S->>L1: Load("Berlin")

    alt L1 hit
        L1-->>S: *Location
        S-->>H: *Location
        H-->>Caller: 200 OK
    else L1 miss
        S->>SF: Do("city:Berlin", func)
        Note over SF: All concurrent "Berlin" callers<br/>wait here; only one proceeds

        SF->>R: FindByCity(ctx, "Berlin")
        R->>DB: SELECT id, city, ST_Y(geo), ST_X(geo)<br/>FROM location WHERE city='Berlin'

        alt L2 hit (DB)
            DB-->>R: row
            R-->>SF: *Location
        else L2 miss
            SF->>G: Geocode(ctx, "Berlin")
            G->>N: GET /search?q=Berlin&format=jsonv2

            alt geocode success
                N-->>G: [{lat:"52.51", lon:"13.38"}]
                G-->>SF: lat=52.51, lon=13.38
            else failure or circuit open
                G-->>SF: default (50.0, 10.0)
            end

            SF->>R: Save(ctx, location)
            R->>DB: INSERT INTO location(id,city,geo,...)<br/>VALUES($1,$2,ST_SetSRID(ST_MakePoint($3,$4),4326),...)
            DB-->>R: saved
            R-->>SF: *Location
        end

        SF-->>S: *Location (shared by all waiters)
        S->>L1: Store("Berlin", *Location)
        S-->>H: *Location
        H-->>Caller: 200 {"id":"...","city":"Berlin",<br/>"latitude":52.51,"longitude":13.38}
    end
```

---

## Geocoder resilience

```mermaid
stateDiagram-v2
    [*] --> Closed

    Closed --> Closed : call succeeds
    Closed --> Open : 5 consecutive failures

    Open --> HalfOpen : 30 s timeout elapses
    Open --> Open : call attempted → rejected immediately

    HalfOpen --> Closed : probe call succeeds
    HalfOpen --> Open : probe call fails

    note right of Open
        Returns immediately with error.
        LocationService falls back to
        default coordinates (50.0, 10.0).
    end note
```

Each call to Nominatim also retries up to 3 times with exponential back-off
(500 ms → ~810 ms → ~1 310 ms) before the circuit breaker records it as a failure.

---

## Profiles integration — strangler-fig

`profiles` calls `location-go` first and falls back to its local `LocationService`
(which also calls Nominatim). The local `location` table in `profiles_db` is kept
as a cache snapshot to maintain the JPA FK on `Profile.location_id`.

```mermaid
sequenceDiagram
    participant PAS as ProfileApplicationService
    participant LSC as LocationServiceClient
    participant LGO as location-go :8065
    participant LR as LocationRepository<br/>(profiles_db)
    participant LS as LocationService<br/>(local fallback)

    PAS->>LSC: resolve("Vienna")

    LSC->>LGO: POST /api/v1/locations/resolve<br/>{"city":"Vienna"}

    alt location-go available (3 s timeout)
        LGO-->>LSC: {id, city:"Vienna", lat:48.20, lon:16.37}

        LSC->>LR: findByCity("Vienna")
        alt local snapshot exists
            LR-->>LSC: Location
        else not cached locally
            LSC->>LR: save(new Location{city, geo})
            LR-->>LSC: Location
        end

        LSC-->>PAS: Location

    else timeout / 5xx / circuit open
        Note over LSC: logs warning, falls back
        LSC->>LS: create("Vienna")
        LS-->>LSC: Location (via local Nominatim)
        LSC-->>PAS: Location
    end
```

---

## Real-time deck rebuild on location change

When a user changes city or GPS coordinates, `profiles` emits a
`ProfileUpdatedEvent{changeType: LOCATION_CHANGE}`. The deck service
reacts by invalidating stale data **and** immediately rebuilding the
viewer's deck from candidates near the new location.

```mermaid
sequenceDiagram
    participant U as User
    participant P as Profiles :8010
    participant K as Kafka<br/>profile.updated
    participant DC as ProfileEventConsumer
    participant RC as DeckCache (Redis)
    participant PH as ProfilesHttp
    participant PL as DeckPipeline

    U->>P: PATCH /api/v1/profiles/me<br/>{city: "Vienna"}
    P->>P: locationServiceClient.resolve("Vienna")
    P->>K: ProfileUpdatedEvent<br/>{changeType: LOCATION_CHANGE}
    P-->>U: 200 updated profile

    K-->>DC: consumeProfileUpdate(LOCATION_CHANGE)

    DC->>RC: invalidate(profileId)
    Note over RC: Removes viewer's personal deck ZSET
    DC->>RC: markProfileInvalidated(profileId)
    Note over RC: Flags profile as stale in all viewers' decks
    DC->>RC: removeFromAllDecks(profileId)
    Note over RC: Eagerly purges this profile from other decks

    DC->>PH: getProfile(profileId)
    PH-->>DC: SharedProfileDto<br/>{location: {lat:48.20, lon:16.37, city:"Vienna"}}

    DC->>PL: rebuildOneDeck(viewer)

    rect rgb(240, 248, 255)
        Note over PL: DeckPipeline.buildDeck(viewer)
        PL->>PL: CandidateSearchStage<br/>search profiles near Vienna (maxRange km)
        PL->>PL: SwipeFilterStage<br/>drop already-swiped candidates
        PL->>PL: ScoringStage<br/>LocationProximityStrategy scores by<br/>distance to Vienna coords
        PL->>RC: CacheStage<br/>write ordered ZSET to Redis
    end

    Note over U: Next GET /deck returns Vienna-area<br/>candidates instantly (no cold rebuild)
```
