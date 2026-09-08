# Inventory of comparable solutions (Wayfinder W1)

Status: **Done** as a fact catalog. This is not an experiment plan and does not choose a runtime.

“Current” means what `docker-compose.yml` runs today. “Simple / other” means a baseline that already exists or would have to be written. Evidence is from this checkout.

## Already comparable (both sides exist)

### EXP-SWIPES — command ingress

| Side | What | Where |
|---|---|---|
| Current | Go `fasthttp` service, bounded Kafka producer, `acks=all` before `202` | `services/swipes-go`, default `swipes` service in `docker-compose.yml` |
| Other | Java Spring `swipes-demo` | `services/swipes-demo`, overlay `docker-compose.swipes-java-rollback.yml` |
| Switch | Compose overlay, no new API | `services/swipes-go/README.md` |
| Load path | Benchmark env + internal auth; historical VPS k6 | `docker-compose.swipes-go-benchmark.yml`, `services/swipes-demo/GO_SERVICE_SPEC.md` § k6, `simple-load-test.sh` |

This is the only pair with an explicit candidate/rollback switch and recorded RPS history.

### EXP-LOCATION — geocode + persist

| Side | What | Where |
|---|---|---|
| Current | Standalone Go service, L1 cache, singleflight, PostGIS | `services/location-go` |
| Other | In-process Java `LocationService` used as fallback when Go is down | `services/profiles/.../LocationServiceClient.java`, `services/location-go/docs/architecture.md` |

Comparable for resolve latency and Nominatim avoidance. Not a drop-in ECS service swap: the Java side lives inside Profiles.

## Extracted; naive baseline is gone from the client path

### EXP-DECKREAD — deck GET

| Side | What | Where |
|---|---|---|
| Current | Quarkus deck-read, Redis Cluster read model, `GET /api/v1/deck` and `/api/v2/deck` | `services/deck-read`, gateway routes in `docker-compose.yml` |
| Other | Old in-Profiles deck read | described as removed in `docs/features/deck-read-cqrs/README.md` |
| Load path | Materialized `GET /api/v2/deck?limit=20` Go bench | `services/deck-read/load-tests/` |

A proof needs either a restored simple reader (SQL or Profiles-path) or a replica-scaling experiment of the current service only (already sketched in the load-test README: 1 replica vs 2).

### EXP-PHOTOS — image bytes

| Side | What | Where |
|---|---|---|
| Current | FastAPI: four JPEG variants, S3, presign | `services/photos` |
| Other | Previous in-Profiles / Match processing | extracted; Profiles now calls Photos (`UploadPhotoService`) |

A “simple” side would be original-only upload. That service does not exist. Product A/B on variant count would change UX and storage cost, not only RPS.

## Would need a disposable simple implementation

### EXP-DECKBUILD — ranking

Current: `DeckPipeline` search → swipe filter → `AgeCompatibilityStrategy` + `LocationProximityStrategy` → Redis ZSET (`services/deck/ARCHITECTURE.md`).

Simple: random or recency SQL list, no Redis reverse index. Not in repo.

### EXP-OUTBOX — match/swipe publish

Current: transactional outbox in Consumer (`MatchOutboxBatchProcessor`, `SwipeOutboxService`).

Simple: publish to Kafka in the same request thread. Not in repo. A RPS win here would likely violate the reliability goal in the root README.

### EXP-DECKCACHE — candidate search

Current: optional preferences Redis set (`deck.preferences-cache-enabled`) plus always-on deck ZSET.

Simple: always hit Profiles `/internal/search`. Partial: the direct mode is already the default pipeline path.

## What this catalog does *not* include

- Frontend Angular variants (out of an ECS service bake-off unless the owner adds them).
- Keycloak vs no-auth (auth is a production invariant; swipes already has a benchmark-only internal path).
- Photos S3 vs local disk (Profiles/Match were deliberately left without a local S3 fallback).

## Implication for v1 (recommendation, not a decision)

The cheapest honest first proof is **EXP-SWIPES**: both images exist, the contract is specified, rollback is one compose overlay, and a JSON-shaped load harness can follow the deck-read bench pattern. Everything else is either a fallback-inside-another-service or a new baseline to write.
