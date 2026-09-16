# swipes-demo

Java swipe-write original. Rollback overlay, not the Compose default.

Parent map: [Lunari README](../../README.md) · Domain: swipes (legacy)

## Boundary

**Owns.** The Spring implementation of `POST /api/v1/swipes` used when the Java overlay is applied.

**Does not own.** The production/demo Compose path — that is [swipes-go](../swipes-go/README.md). Match detection ([consumer](../consumer/README.md)).

**Neighbors.** `docker-compose.swipes-java-rollback.yml` swaps this image in for `swipes`.

## Contents

Spring Boot swipe persistence + Kafka producer. Load-test notes: [load-tests/README.md](load-tests/README.md).

Port `:8040` (same as swipes-go; they never bind on the same host at once).

## Decision

**Failure.** Replacing Java in-place with no rollback made a Go bug a full write outage.

**Rejected.** Delete the Java service once Go compiled.

**Shipped.** Go is default in `docker-compose.yml`. This module stays as an explicit overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.swipes-java-rollback.yml up -d swipes
```

**Cost.** Two code paths for the same HTTP contract. Do not start this for `./scripts/demo-up.sh`.

## Validate

```bash
mvn test
```
