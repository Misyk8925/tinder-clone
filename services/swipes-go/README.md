# Swipes Go

High-throughput candidate replacement for `swipes-demo`. The service uses direct
`fasthttp` routing and practical technical packages under `internal/`:

- `router`: HTTP routes, status mapping, health and metrics
- `service`: swipe validation, ownership and authorization rules
- `kafka`: bounded producer and profile lifecycle consumers
- `config`, `security`, `repository`, `client`, `model`, `metrics`, `utils`

Production startup fails unless JWT/JWKS, PostgreSQL, Redis, Kafka, and the
central `profile_cache` migration are ready. The process never creates schema.
The only exception is `SWIPES_INTERNAL_ONLY_BENCHMARK=true`: it is rejected
outside `APP_ENV=benchmark` and additionally requires the trusted benchmark
secret, profile-check bypass, and disabled profile-cache consumers. It exists
solely for the disposable k3s/k6 topology in
[`infra/k3s/swipes-go-loadtest`](../../infra/k3s/swipes-go-loadtest).

The bounded producer still batches work across fixed workers, but `202 Accepted`
is returned only after an `acks=all` Kafka write succeeds. Profile lifecycle
consumers retry locally, publish exhausted records to `<topic>.dlt` with
`acks=all`, and commit the source offset only after processing or DLT recovery.

## Validation

```shell
GOCACHE=/tmp/tinder-swipes-go-cache go test ./...
GOCACHE=/tmp/tinder-swipes-go-cache go test -race ./...
GOCACHE=/tmp/tinder-swipes-go-cache go vet ./...
GOCACHE=/tmp/tinder-swipes-go-cache go test -run '^$' -bench . -benchmem ./internal/...
```

## Candidate and rollback wiring

Go swipes is the default in `docker-compose.yml`. Photos is the Python service at `services/photos`; location is `services/location-go`.

The explicit Java rollback overlay is:

```shell
docker compose -f docker-compose.yml -f docker-compose.swipes-java-rollback.yml up -d swipes
```

Run the isolated internal-auth fast path only in the benchmark environment:

```shell
docker compose -f docker-compose.yml -f docker-compose.swipes-go.yml \
  -f docker-compose.swipes-go-benchmark.yml up -d swipes
```

Keep `docker-compose.swipes-java-rollback.yml` for a Java rollback. Do not use
the Go overlay except with the benchmark file for the isolated internal-auth path.
