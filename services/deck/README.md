# Deck

Write-side discovery builder: ranked order, reverse-index invalidation, `ensure` on cache miss.

Parent map: [Lunari README](../../README.md) · Domain: discover · Feature boundary: [deck-read-cqrs](../../docs/features/deck-read-cqrs/README.md)

## Boundary

**Owns.** Build/score/cache pipeline, Redis keys for *ordering*, reverse indexes so a profile change invalidates only affected decks, `POST /api/v1/internal/deck/ensure`, admin rebuild/exists/size/invalidate, `deck.built.v1`, popularity like-rate store, ranking experiment registry.

**Does not own.** Client-facing `GET /api/v1|v2/deck` ([deck-read](../deck-read/README.md)). Card payload hydration. Deck Read’s Redis Cluster. The browser never calls this service.

**Neighbors.** [deck-read](../deck-read/README.md) calls `ensure` and reads ordering/timestamp only. [profiles](../profiles/README.md) internal search/by-ids/active. [consumer](../consumer/README.md) swipe counts and experiment outcomes. Admin UI: [tinder-admin](../../clients/tinder-admin/README.md). Offline screen: [deck-sim](load-tests/deck-sim/README.md). Types: [tinder-contracts](../tinder-contracts/README.md) (`DeckEntry`, `DeckRedisKeys`).

Gateway exposes only `/api/v1/admin/deck/**`, `/api/v1/admin/experiments/**`, `/api/v1/admin/popularity/**` (ADMIN). Ranking admin is **not** the live Discover default.

## Contents

- Pipeline: candidate search → swipe filter → scoring → cache.
- Scoring: `AgeCompatibilityStrategy`, `LocationProximityStrategy`, reciprocal age/proximity, `PopularityStrategy`, `VariantAssigner`.
- `SwipeEventConsumer` updates like rates.
- `ExperimentStore` / `ExperimentAdminController` / `PopularityAdminController`.
- Port `:8030`.

## Decision

**Failure.** Building order from Profiles + location + swipe history on every Discover GET couples user latency to write-side work. A profile change must not rebuild every deck.

**Rejected.** One Spring service that both ranks and serves. Deck Read absorbing this algorithm or these Redis keys.

**Shipped.** Deck stays the builder. Proof the read side does not drift: `DeckReadCqrsBoundaryAcceptanceTest` in deck-read.

**Cost.** Extra hop on a cold deck. Popularity / experiments are in-repo but not claimed as the production Discover default (`ADMIN` vs `USER_ADMIN` is still an owner decision). See [deck-popularity-ranker](../../docs/features/deck-popularity-ranker/README.md) and [deck-ranking-experiments](../../docs/features/deck-ranking-experiments/00-state.md).

## Validate

```bash
mvn -B -DskipTests install -f ../tinder-contracts/pom.xml
mvn test
```
