# Profiles

Write-side profile aggregate: CRUD, preferences, photo catalogue, outbox events, premium role in Keycloak.

Parent map: [Lunari README](../../README.md) · Domain: profile

## Boundary

**Owns.** Profile JSON, matching preferences, photo *slots* (not object bytes), `profile.created|updated|deleted` and `profile.deck-card-projection.v1` via transactional outbox, public photo URLs under `/api/v1/profiles/photos/**`, gRPC `UpdatePremiumUser`, deck-card backfill job, internal mTLS API on `:8011`.

**Does not own.** S3 bytes and JPEG variants ([photos](../photos/README.md)). Geocoding and PostGIS location rows ([location-go](../location-go/README.md)). Client-facing deck reads ([deck-read](../deck-read/README.md)). Ranking ([deck](../deck/README.md)).

**Neighbors.** Called by the [gateway](../gateway/README.md) on `:8010`. Internal callers: [deck](../deck/README.md), [deck-read](../deck-read/README.md) (projection, not live lookup on the required read path), [subscriptions](../subscriptions/README.md) over mTLS gRPC. Calls [location-go](../location-go/README.md), [photos](../photos/README.md), and [moderation](../moderation/README.md) when `MODERATION_ENABLED=true`. Shared types: [tinder-contracts](../tinder-contracts/README.md).

The browser never calls `:8011`.

## Contents

Hexagonal, package-by-feature:

- `api/` — REST, gRPC, schedulers (premium expiry, deleted-profile cleanup).
- `application/` — use cases and ports (`CreateProfileService`, `PhotoMediaPort`, `LocationPort`, `DomainEventPublisherPort`).
- `domain/` — profile rules, no Spring/JPA.
- `infrastructure/` — JPA, Redis cache, outbox (`ProfileOutboxService`, `ProfileOutboxBatchProcessor`), location/photos/moderation/Keycloak adapters.

Public `:8010`: `/api/v1/profiles` CRUD, `/me`, `/by-ids`. Internal `:8011`: `id-by-user`, `search`, `by-ids`, `active` feed for deck rebuild.

## Decision

**Failure.** Discover used to live on this JVM. A profile write or a missed cache entry sat on the hottest GET. Location and S3 failed independently of profile JSON.

**Rejected.** Keep deck-read and geocoding inside Profiles. Dual-write DB + Kafka in the request thread. SQL-seed profiles for demos.

**Shipped.** Profiles shrinks to the write aggregate. Outbox in the same transaction as the row. A SQL insert never emits `profile.created`, so Deck Read stays `202 BUILDING` — see [seed-notes](../../docs/demo/seed-notes.md).

**Cost.** Extra hops to location and photos. At-least-once delivery; consumers must be idempotent.

## Validate

Install [tinder-contracts](../tinder-contracts/README.md) first, then:

```bash
mvn -B -DskipTests install -f ../tinder-contracts/pom.xml
mvn test
```

Some tests use Testcontainers (PostgreSQL, Redis, Kafka).
