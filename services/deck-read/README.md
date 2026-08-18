# Deck Read runtime roles

`deck-read` is one Quarkus artifact and one Docker image. The active profile selects only the process role:

- `prod,api`: HTTP plus the materialization-request producer; all incoming materializers are disabled.
- `prod,worker`: Kafka materializers and reconciliation; its HTTP port `8041` is internal health/metrics only.
- `all`: local HTTP and materializers together, using local standalone Redis defaults.

Production Compose exposes `deck-read-api` and `deck-read-worker` without fixed container names, so they can be scaled independently:

```bash
docker compose up -d --scale deck-read-api=2 --scale deck-read-worker=2
```

For the staged rollout, start/scale the workers first. Keep the compatibility read path enabled with
`DECK_READ_MATERIALIZED_REQUIRED=false`, wait for coverage and Kafka lag to satisfy the rollout gate,
then restart only the API role with `DECK_READ_MATERIALIZED_REQUIRED=true`. In required mode an unknown
viewer receives `202` and an atomically coalesced background request; no synchronous build is performed.

For a local all-in-one process after starting the focused development dependencies:

```bash
docker compose -f docker-compose.deck-read-dev.yml up -d
QUARKUS_PROFILE=all mvn -f services/deck-read/pom.xml quarkus:dev
```
