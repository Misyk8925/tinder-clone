# Swipes Go

High-throughput candidate replacement for `swipes-demo`. The service uses direct
`fasthttp` routing and practical technical packages under `internal/`:

- `router`: HTTP routes, status mapping, health and metrics
- `service`: swipe validation, ownership and authorization rules
- `kafka`: bounded producer and profile lifecycle consumers
- `config`, `security`, `repository`, `client`, `model`, `metrics`, `utils`

Production startup fails unless JWT/JWKS, PostgreSQL, Redis, Kafka, and the
central `profile_cache` migration are ready. The process never creates schema.

## Validation

```shell
GOCACHE=/tmp/tinder-swipes-go-cache go test ./...
GOCACHE=/tmp/tinder-swipes-go-cache go test -race ./...
GOCACHE=/tmp/tinder-swipes-go-cache go vet ./...
GOCACHE=/tmp/tinder-swipes-go-cache go test -run '^$' -bench . -benchmem ./internal/...
```

## Candidate and rollback wiring

The main Compose file continues to use Java until the paired contract,
dependency-failure, event-delivery, and load-test gates are recorded. Run the Go
candidate with:

```shell
docker compose -f docker-compose.yml -f docker-compose.swipes-go.yml up -d swipes
```

The explicit rollback overlay is:

```shell
docker compose -f docker-compose.yml -f docker-compose.swipes-java-rollback.yml up -d swipes
```

Run the isolated internal-auth fast path only in the benchmark environment:

```shell
docker compose -f docker-compose.yml -f docker-compose.swipes-go.yml \
  -f docker-compose.swipes-go-benchmark.yml up -d swipes
```

After three same-environment runs at 8,000 requests/second for 120 seconds meet
the documented zero-loss and latency gates, promote the Go build stanza into
`docker-compose.yml`. Retain the Java rollback overlay for the seven-day soak.
