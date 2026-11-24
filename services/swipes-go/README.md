# Swipes Go

Production and demo swipe-write path (Compose). `services/swipes-demo` is the Java original kept as a rollback overlay, not what you start for a screening.

The service uses direct `fasthttp` routing and practical technical packages under `internal/`:

- `router`: HTTP routes, status mapping, health and metrics
- `service`: swipe validation, ownership and authorization rules
- `kafka`: bounded producer and profile lifecycle consumers
- `config`, `security`, `repository`, `client`, `model`, `metrics`, `utils`

Production startup fails unless JWT/JWKS, PostgreSQL, Redis, Kafka, and the
central `profile_cache` migration are ready. The process never creates schema.

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

A Java rollback means pointing the `swipes` build context in `docker-compose.yml` back at
`./services/swipes-demo` (with `SPRING_PROFILES_ACTIVE: prod`). The isolated internal-auth fast path
(`APP_ENV=benchmark`, `SWIPES_INTERNAL_BYPASS_PROFILE_CHECK=true`) is for benchmark environments only;
set those variables there, never in the production stack.
