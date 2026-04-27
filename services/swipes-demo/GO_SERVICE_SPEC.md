# Go Rewrite Specification For `swipes-demo`

This document specifies the current Java/Spring `swipes-demo` behavior that a Go implementation must preserve. It is written from the reverted service code in `services/swipes-demo/src/main/java/com/example/swipes_demo` and the VPS k6 benchmark conditions that were reproduced during the RPS investigation.

## 1. Scope

The service is a command-ingress service for swipe decisions.

It must:

1. Accept swipe commands over HTTP.
2. Authenticate requests with either JWT or trusted internal auth.
3. Enforce the same swipe validation and premium/super-like rules.
4. Keep a local profile-existence cache from profile lifecycle Kafka events.
5. Publish accepted swipes to Kafka topic `swipe-created`.
6. Provide a benchmark fast path for trusted internal no-JWT load tests.

The service is not responsible for match creation, feed/deck selection, liked-me queries, subscription decisions, or profile creation.

## 2. Runtime Dependencies

Required dependencies:

| Dependency | Use |
| --- | --- |
| Kafka | Produce `swipe-created`; consume `profile.created` and `profile.deleted`. |
| PostgreSQL | Persistent `profile_cache` table. |
| Redis | Fast set membership cache for existing profile IDs. |
| Profiles service | Fallback existence check for cache misses. |
| Keycloak/JWK endpoint | JWT validation for public/direct authenticated requests. |

Default service port: `8040`.

The Docker deployment currently hides the port from the public host and exposes it only inside the compose network. Public traffic normally reaches it through the gateway.

## 3. Configuration

The Go service should support the same effective configuration keys through environment variables.

| Env var | Current default | Meaning |
| --- | ---: | --- |
| `SWIPES_DB_URL` / `SPRING_DATASOURCE_URL` | local Postgres URL | PostgreSQL connection string. |
| `SWIPES_DB_USER` / `SPRING_DATASOURCE_USERNAME` | `swipes_app` | PostgreSQL user. |
| `SWIPES_DB_PASSWORD` / `SPRING_DATASOURCE_PASSWORD` | required in Docker | PostgreSQL password. |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host. |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | local Keycloak URL | JWK set URL for JWT verification. |
| `SERVICES_PROFILES_BASE_URL` | `http://localhost:8010/api/v1/profiles` | Profiles API base URL. |
| `INTERNAL_SWIPES_AUTH_SECRET` | empty | Shared secret for `X-Internal-Auth`. Empty means internal auth is disabled. |
| `SWIPES_INTERNAL_BYPASS_PROFILE_CHECK` | `false` | When true, trusted internal controller requests bypass UUID parsing and profile existence checks. The fast path already bypasses these checks. |
| `SWIPES_PRODUCER_QUEUE_CAPACITY` | `200000` | Max queued swipe events before HTTP returns `429`. |
| `SWIPES_PRODUCER_CONCURRENCY` | `4` | Number of producer drain workers. |
| `SWIPES_PRODUCER_WORKER_COUNT` | `4` | Legacy alias used if concurrency is absent. |
| `SWIPES_PRODUCER_BATCH_SIZE` | `500` | Max events drained per batch. |
| `SWIPES_PRODUCER_BUFFER_TIMEOUT` | `1ms` | Delay when the producer queue is empty. |
| `SWIPES_PRODUCER_WARMUP_ENABLED` | `true` | Warm Kafka producer metadata on startup. |

Kafka producer defaults to preserve:

1. `acks=1`
2. `batch.size=131072`
3. `linger.ms=20`
4. `buffer.memory=67108864`
5. string key serializer
6. string value serializer

## 4. HTTP API

All successful swipe commands return `202 Accepted` with an empty body. The current service responds after enqueueing to the local producer queue, not after Kafka acknowledges the record.

### `POST /api/v1/swipes`

Creates a normal swipe.

Request JSON:

```json
{
  "profile1Id": "249bea58-449e-4bb6-9243-8f16efec14e0",
  "profile2Id": "44799e38-8299-4697-a8a1-2c56ccededfd",
  "decision": true,
  "isSuper": false
}
```

Fields:

| Field | Type | Required | Semantics |
| --- | --- | --- | --- |
| `profile1Id` | string | yes | Swiping profile ID. Public/JWT path must be a UUID. Trusted fast path only requires a non-blank string. |
| `profile2Id` | string | yes | Target profile ID. Public/JWT path must be a UUID. Trusted fast path only requires a non-blank string. |
| `decision` | boolean | no | `true` means right swipe/like; `false` means left swipe/pass. Missing defaults to `false`. Public/controller JSON can coerce `null` to `false`; the trusted fast path rejects present non-boolean values. |
| `isSuper` | boolean | no | Missing defaults to `false`. Public/controller JSON treats `null` as false; the trusted fast path rejects present `null` as an invalid field. |

Auth behavior:

1. If a valid `X-Internal-Auth` header is present, the current Java service takes the high-priority internal fast path before the Spring controller/security path.
2. That fast path calls `sendTrustedInternalSwipe(body, false)`.
3. A super-like payload (`isSuper: true`) on this route is rejected with `403`, even with valid internal auth.
4. Without valid internal auth, a valid JWT is required.

### `POST /api/v1/swipes/super`

Premium/admin-authorized swipe route.

The request body has the same shape as `/api/v1/swipes`.

Auth behavior:

1. With valid `X-Internal-Auth`, the controller treats the request as trusted internal and passes `isPremiumOrAdmin=true`.
2. Without internal auth, the service accepts any valid JWT at the service layer.
3. The premium/admin enforcement for public traffic is expected to happen in the gateway `PremiumOrAdminFilter`; the swipes service itself does not inspect roles.
4. The route does not force `isSuper=true`. The request body still controls the produced event's `isSuper` value. Clients must send `"isSuper": true` to create a super-like event.

### Health

The current Docker health check probes `GET /actuator/health` and accepts `200`, `401`, `403`, or `404`. A Go service should implement a real `GET /actuator/health` returning `200 OK` when the process is live and dependencies needed for startup were initialized.

## 5. Authentication And Authorization

JWT:

1. Validate bearer JWTs using the configured JWK set URL.
2. Treat missing or blank JWT as `401 Missing JWT principal` on public service logic.
3. Preserve the original bearer token string so profile-service fallback calls can forward it as `Authorization: Bearer <token>`.

Internal auth:

1. Header name: `X-Internal-Auth`.
2. The configured secret must be non-blank.
3. The candidate header must be non-blank.
4. Compare the configured secret and candidate in constant time.
5. A valid internal auth header allows the request through without JWT.

Invalid internal auth edge case:

1. If the header is present but invalid and no JWT is present, the request is denied by security.
2. If the header is present but invalid and a valid JWT is present, the request is processed as a normal public/JWT request.

## 6. Swipe Business Rules

Apply these rules in order.

### Super-like permission

If `isSuper == true` and `isPremiumOrAdmin == false`, reject:

```text
HTTP 403
Super like requires a premium or admin account
```

`isPremiumOrAdmin` is true only for `/api/v1/swipes/super`; for public traffic the gateway is responsible for making sure only premium/admin users reach that route. The flag only authorizes super-like payloads; it does not mutate the request into a super-like.

### Profile ID validation

Public/JWT path:

1. `profile1Id` and `profile2Id` must be valid UUID strings.
2. Invalid UUID rejects with:

```text
HTTP 400
Invalid UUID in field: profile1Id
```

or:

```text
HTTP 400
Invalid UUID in field: profile2Id
```

3. Equal UUIDs reject with:

```text
HTTP 400
profile1Id and profile2Id must be different
```

Trusted internal bypass path:

1. Valid internal `POST /api/v1/swipes` fast path does not parse UUIDs.
2. Valid internal controller path with `SWIPES_INTERNAL_BYPASS_PROFILE_CHECK=true` does not parse UUIDs.
3. These paths only reject equal raw profile ID strings.

### Profile existence

Profile existence is required unless the request is trusted internal and bypassed.

For public/JWT requests:

1. Check Redis set `profiles:exists` for both IDs.
2. If both are in Redis, accept.
3. For Redis misses, check PostgreSQL table `profile_cache`.
4. Warm Redis for IDs found in PostgreSQL.
5. For IDs still missing, call profiles service fallback.
6. If profiles service confirms all missing IDs, insert them into PostgreSQL with `userId="unknown"` and `createdAt=now`, warm Redis, and accept.
7. If any ID is not confirmed, reject:

```text
HTTP 404
One or both profiles were not found
```

Profiles fallback:

1. Endpoint: `GET {SERVICES_PROFILES_BASE_URL}/by-ids?ids=<comma-separated-uuid-list>`.
2. Forward `Authorization: Bearer <token>` when a JWT token is available.
3. Parse response items as objects containing at least `profileId`.
4. Ignore unknown fields.
5. On HTTP/client/deserialization error, return an empty found set and therefore fail closed.

## 7. Trusted Internal Fast Path Compatibility

The Java fast path only applies to:

```text
POST /api/v1/swipes
```

with a valid `X-Internal-Auth` header.

It does not apply to:

```text
POST /api/v1/swipes/super
```

The current implementation reads the full body into memory as UTF-8 text and extracts fields with simple string scanning:

1. Missing/blank body rejects with `400 Swipe body is required`.
2. Missing `profile1Id` or `profile2Id` rejects with `400 Missing field: <field>`.
3. Non-string `profile1Id` or `profile2Id` rejects with `400 Invalid field: <field>`.
4. Blank string ID rejects with `400 Missing field: <field>`.
5. Missing `decision` defaults to `false`.
6. Missing `isSuper` defaults to `false`.
7. Present non-boolean `decision` or `isSuper`, including explicit JSON `null`, rejects with `400 Invalid field: <field>`.
8. Equal raw ID strings reject with `400 profile1Id and profile2Id must be different`.
9. It enqueues directly without profile existence checks.

For the Go rewrite, prefer a strict JSON decoder for production correctness, but keep these externally visible outcomes for valid JSON requests used by clients and k6. Exact reproduction of Java's malformed-JSON leniency is not a production requirement unless compatibility tests explicitly depend on it.

## 8. Kafka Produced Event Contract

Topic:

```text
swipe-created
```

Record key:

```text
profile1Id
```

Value JSON:

```json
{
  "eventId": "0000019a-0000-0000-7f00-000000000001",
  "profile1Id": "249bea58-449e-4bb6-9243-8f16efec14e0",
  "profile2Id": "44799e38-8299-4697-a8a1-2c56ccededfd",
  "decision": true,
  "isSuper": false,
  "timestamp": 1777190000000
}
```

Fields:

| Field | Type | Meaning |
| --- | --- | --- |
| `eventId` | string | UUID-format unique ID. Current Java constructs it from `currentTimeMillis` plus an atomic sequence, not UUIDv4. |
| `profile1Id` | string | Swiping profile ID exactly as accepted by the request path. |
| `profile2Id` | string | Target profile ID exactly as accepted by the request path. |
| `decision` | boolean | Swipe decision. |
| `isSuper` | boolean | Super-like flag. |
| `timestamp` | integer | Unix epoch milliseconds at event creation time. |

Producer semantics:

1. HTTP requests enqueue an event into an in-process bounded queue.
2. If the queue is full, reject the HTTP request:

```text
HTTP 429
Swipe producer queue is full
```

3. Background workers drain up to `SWIPES_PRODUCER_BATCH_SIZE` events per batch.
4. Kafka send failures after enqueue are logged and swallowed. The current HTTP client is not notified.
5. On startup, warm producer metadata for `swipe-created` when warmup is enabled.

The Go service should preserve this request/queue boundary if it is replacing the current service. A direct synchronous Kafka send on the request path changes latency and failure semantics.

## 9. Profile Cache Event Contracts

### PostgreSQL table

Table name:

```text
profile_cache
```

Columns:

| Column | Type | Rules |
| --- | --- | --- |
| `profile_id` | UUID | Primary key. |
| `user_id` | text | Non-null. |
| `created_at` | timestamp | Non-null. |

The Java service uses Hibernate `ddl-auto=update`. A Go rewrite should provide an explicit migration that creates the same table and indexes instead of relying on runtime schema mutation.

### Redis

Key:

```text
profiles:exists
```

Type: set.

Members: profile UUID strings.

### Consumed topic `profile.created`

Payload:

```json
{
  "eventId": "36d82409-cecc-4f04-9653-9c9db0c5b4bb",
  "profileId": "249bea58-449e-4bb6-9243-8f16efec14e0",
  "timestamp": "2026-04-26T09:00:00Z",
  "userId": "keycloak-user-id"
}
```

Handling:

1. If `profileId` is null, skip the event.
2. If `timestamp` is null, use current time.
3. If `userId` is null, use `"unknown"` for new rows.
4. If the row exists, update `createdAt`; update `userId` only when the event has a non-null userId.
5. If the row does not exist, insert it.
6. Add the profile ID string to Redis set `profiles:exists`.
7. Redis write errors are logged and suppressed.

### Consumed topic `profile.deleted`

Payload:

```json
{
  "eventId": "36d82409-cecc-4f04-9653-9c9db0c5b4bb",
  "profileId": "249bea58-449e-4f04-9653-9c9db0c5b4bb",
  "timestamp": "2026-04-26T09:00:00Z"
}
```

Handling:

1. Delete the `profile_cache` row when it exists.
2. Log a warning when it does not exist.
3. Remove the profile ID string from Redis set `profiles:exists`.
4. Redis remove errors are logged and suppressed.

Kafka consumer details to match:

1. Listener group ID in annotations: `swipe-service`.
2. `auto.offset.reset=earliest`.
3. `enable.auto.commit=false`.
4. `isolation.level=read_committed`.
5. Batch ack mode.
6. JSON payloads should not require type-info headers.

## 10. Error Mapping

Return these statuses for service-level failures:

| Condition | Status | Reason |
| --- | ---: | --- |
| Missing/invalid authentication | `401` or security-layer denial | Current Spring security handles this before controller. |
| Internal route header invalid and no JWT reaches controller | `403` | `Invalid internal auth`. |
| Missing JWT principal in public service logic | `401` | `Missing JWT principal`. |
| Super-like without premium/admin path | `403` | `Super like requires a premium or admin account`. |
| Body missing on trusted fast path | `400` | `Swipe body is required`. |
| Missing required ID field | `400` | `Missing field: <field>`. |
| Invalid trusted fast-path field shape | `400` | `Invalid field: <field>`. |
| Invalid UUID on public path | `400` | `Invalid UUID in field: <field>`. |
| Same profile IDs | `400` | `profile1Id and profile2Id must be different`. |
| One or both profiles missing | `404` | `One or both profiles were not found`. |
| Producer queue full | `429` | `Swipe producer queue is full`. |

## 11. Go Implementation Guidance

Recommended stack:

1. HTTP server: standard `net/http` or `fasthttp`.
2. Kafka: `segmentio/kafka-go` for simple implementation or `confluent-kafka-go` for librdkafka-backed maximum throughput.
3. Redis: `github.com/redis/go-redis/v9`.
4. PostgreSQL: `pgx/v5`.
5. JWT/JWK: `github.com/lestrrat-go/jwx/v2` or `github.com/MicahParks/keyfunc/v3`.

For this service, `net/http` plus `confluent-kafka-go` is the safest high-throughput target: Go's standard server is already efficient enough for this payload size, and librdkafka gives strong async batching/backpressure behavior. Use `fasthttp` only if profiling proves HTTP parsing is the remaining bottleneck and compatibility tradeoffs are acceptable.

Internal components:

1. `Config`: parse env, set defaults, validate required values.
2. `AuthMiddleware`: accept valid internal auth or valid JWT.
3. `SwipeHandler`: route `/api/v1/swipes` and `/api/v1/swipes/super`.
4. `SwipeService`: implement business rules and event construction.
5. `ProfileCache`: Redis/DB/profiles-service existence check.
6. `SwipeProducer`: bounded queue plus Kafka batch sender.
7. `ProfileEventConsumer`: consume create/delete profile events and mutate cache.
8. `HealthHandler`: return live/readiness status.

Concurrency rules:

1. Keep HTTP handlers non-blocking with respect to Kafka broker acks; enqueue only.
2. Use a bounded channel or lock-free ring queue for producer backpressure.
3. Preserve `429` when enqueue fails.
4. Drain batches in fixed worker goroutines.
5. Flush producer on shutdown with a bounded timeout.
6. Use context deadlines for Redis, PostgreSQL, profiles-service, and Kafka metadata calls.

## 12. Acceptance Tests For The Go Service

Minimum behavior tests:

1. Public `/api/v1/swipes` with valid JWT, existing profiles, `isSuper` absent returns `202` and publishes `isSuper:false`.
2. Public `/api/v1/swipes` with invalid UUID returns `400`.
3. Public `/api/v1/swipes` with equal UUIDs returns `400`.
4. Public `/api/v1/swipes` when one profile is absent from Redis/DB/profiles fallback returns `404`.
5. Public `/api/v1/swipes` with `isSuper:true` returns `403`.
6. Public `/api/v1/swipes/super` with valid JWT, existing profiles, and `isSuper:true` returns `202` and publishes `isSuper:true`.
7. Public `/api/v1/swipes/super` with valid JWT, existing profiles, and `isSuper` absent/false returns `202` and publishes `isSuper:false`.
8. Internal `/api/v1/swipes` with valid `X-Internal-Auth` and no JWT returns `202` without Redis/Postgres/profiles calls.
9. Internal `/api/v1/swipes` with valid `X-Internal-Auth` and `isSuper:true` returns `403`.
10. Internal `/api/v1/swipes/super` with valid `X-Internal-Auth` and `isSuper:true` returns `202`.
11. Missing body on trusted fast path returns `400`.
12. Missing `profile1Id` or `profile2Id` returns `400`.
13. Full producer queue returns `429`.
14. Produced Kafka value has exactly the expected field names, including `isSuper`.
15. Produced Kafka key equals `profile1Id`.
16. `profile.created` inserts/updates PostgreSQL and adds Redis set membership.
17. `profile.deleted` deletes PostgreSQL and removes Redis set membership.
18. Profiles fallback forwards bearer token when available and fails closed on remote error.

Benchmark acceptance:

1. Direct internal fast path should show zero HTTP failures at 8k target under clean VPS conditions.
2. A 12k target should be treated as a saturation test. Passing means low/zero dropped iterations, not only zero HTTP failures.
3. Record actual achieved `http_reqs/s`, `dropped_iterations`, p95, p99, max, CPU usage, Kafka lag, and queue depth.

## 13. Ideal VPS Benchmark Conditions

These are the conditions that repeatedly produced the best direct-swipes numbers during the RPS investigation. They are intended for apples-to-apples service comparisons, not as a production operating mode.

Host and paths:

```text
SSH alias: my-vps
Dokploy compose path: /etc/dokploy/compose/tinderclone-tinderclone-61rkjc/code
k6 path: /opt/tinder-clone/k6/swipes-demo
Compose project: tinderclone-tinderclone-61rkjc
Kafka container: tinderclone-tinderclone-61rkjc-kafka-1
Kafka bootstrap inside app network: kafka:29092
Direct target: http://swipes:8040/api/v1/swipes
```

Service settings for direct benchmark mode:

```text
SWIPES_INTERNAL_BYPASS_PROFILE_CHECK=true
SWIPES_PRODUCER_QUEUE_CAPACITY=300000
SWIPES_PRODUCER_CONCURRENCY=4
SWIPES_PRODUCER_BATCH_SIZE=500
SWIPES_PRODUCER_BUFFER_TIMEOUT=1ms
SWIPES_PRODUCER_WARMUP_ENABLED=true
INTERNAL_SWIPES_AUTH_SECRET=<same value as k6 INTERNAL_SWIPES_AUTH_SECRET>
```

Optional JVM settings that helped before the revert:

```text
-Dreactor.netty.ioWorkerCount=8
-XX:ActiveProcessorCount=6
```

The latest reverted compose uses default `JAVA_TOOL_OPTIONS` without the Netty worker override, so do not assume this tuning is active unless it is explicitly present.

Infrastructure preparation:

1. Stop noisy downstream services that consume or generate load:

```bash
docker stop consumer deck subscriptions match || true
```

2. Clean and recreate the hot topic before each serious run:

```bash
docker stop swipes
docker exec tinderclone-tinderclone-61rkjc-kafka-1 bash -lc \
  'kafka-topics --bootstrap-server kafka:29092 --delete --topic swipe-created || true'
docker exec tinderclone-tinderclone-61rkjc-kafka-1 bash -lc \
  'until ! kafka-topics --bootstrap-server kafka:29092 --describe --topic swipe-created >/dev/null 2>&1; do sleep 1; done'
docker exec tinderclone-tinderclone-61rkjc-kafka-1 bash -lc \
  'kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic swipe-created --partitions 32 --replication-factor 1'
docker start swipes
```

3. Wait for `swipes` to be healthy and for Kafka producer warmup logs to appear.
4. Keep k6 on the Docker app network for direct container-name access.
5. For the container-network-sharing variant, remove `K6_DOCKER_NETWORK` from the env file and run k6 with `K6_DOCKER_NETWORK=container:swipes` and `DIRECT_SWIPES_BASE_URL=http://127.0.0.1:8040`.
6. Use no JWT for direct swipes. The trusted header must be present.
7. Use `UUID_MODE=incremental`, `RANDOM_PROFILES=1`, `BENCHMARK_FAST_MODE=1`, and `DISCARD_RESPONSE_BODIES=1`.

Warmup example:

```bash
cd /opt/tinder-clone/k6/swipes-demo
TARGETS=direct-swipes \
DIRECT_SWIPES_BASE_URL=http://swipes:8040 \
BENCHMARK_EXECUTOR=constant-arrival-rate \
BENCHMARK_RATE=2500 \
BENCHMARK_DURATION=3s \
BENCHMARK_PREALLOCATED_VUS=500 \
BENCHMARK_MAX_VUS=1500 \
BENCHMARK_FAST_MODE=1 \
DISCARD_RESPONSE_BODIES=1 \
DEBUG_RESPONSE=0 \
RANDOM_PROFILES=1 \
UUID_MODE=incremental \
SWIPE_EXPECTED_STATUS=202 \
K6_FORCE_DOCKER=1 \
./run-k6-swipe-test.sh -- --summary-trend-stats='avg,p(95),p(99),max'
```

12k direct target example:

```bash
cd /opt/tinder-clone/k6/swipes-demo
TARGETS=direct-swipes \
DIRECT_SWIPES_BASE_URL=http://swipes:8040 \
BENCHMARK_EXECUTOR=constant-arrival-rate \
BENCHMARK_RATE=12000 \
BENCHMARK_DURATION=10s \
BENCHMARK_PREALLOCATED_VUS=1800 \
BENCHMARK_MAX_VUS=9000 \
BENCHMARK_FAST_MODE=1 \
DISCARD_RESPONSE_BODIES=1 \
DEBUG_RESPONSE=0 \
RANDOM_PROFILES=1 \
UUID_MODE=incremental \
SWIPE_EXPECTED_STATUS=202 \
DIRECT_SWIPES_THRESHOLD_P95_MS=100 \
K6_FORCE_DOCKER=1 \
./run-k6-swipe-test.sh -- --summary-trend-stats='avg,p(95),p(99),max'
```

Container-network-sharing 12k variant:

```bash
cd /opt/tinder-clone/k6/swipes-demo
grep -Ev '^(JWT_TOKEN|K6_DOCKER_NETWORK)=' .env.k6.benchmark > /tmp/swipes-direct-container.env
ENV_FILE=/tmp/swipes-direct-container.env \
K6_DOCKER_NETWORK=container:swipes \
TARGETS=direct-swipes \
DIRECT_SWIPES_BASE_URL=http://127.0.0.1:8040 \
BENCHMARK_EXECUTOR=constant-arrival-rate \
BENCHMARK_RATE=12000 \
BENCHMARK_DURATION=10s \
BENCHMARK_PREALLOCATED_VUS=1800 \
BENCHMARK_MAX_VUS=9000 \
BENCHMARK_FAST_MODE=1 \
DISCARD_RESPONSE_BODIES=1 \
DEBUG_RESPONSE=0 \
RANDOM_PROFILES=1 \
UUID_MODE=incremental \
SWIPE_EXPECTED_STATUS=202 \
K6_FORCE_DOCKER=1 \
./run-k6-swipe-test.sh -- --summary-trend-stats='avg,p(95),p(99),max'
```

Restore after benchmark:

1. Recreate `swipe-created` again so downstream services do not start against an overloaded topic.
2. Ensure `swipes` is running with the intended environment.
3. Start downstream services again:

```bash
docker start match subscriptions deck consumer || true
```

## 14. Known Benchmark Baselines

These are historical direct-swipes k6 runs from `/opt/tinder-clone/k6/swipes-demo/runs`. They are not hard guarantees; they are reference points for comparing a Go replacement under the same setup.

| Run | Target | Result | Notes |
| --- | ---: | ---: | --- |
| `swipes-fastpath-filter-12000-20260425-195636.log` | 12k/s | `8810.391312/s`, p95 `258.49ms`, failures `0/88191`, dropped `31810` | Best normal app-network run before later experiments. |
| `swipes-fastpath-container-net-12000-20260425-200810.log` | 12k/s | `9132.476495/s`, p95 `277.6ms`, failures `0/91661`, dropped `28298` | Best corrected `container:swipes` network run. |
| `swipes-netty-w8-12000-20260425-204224.log` | 12k/s | `9095.190229/s`, p95 `218.99ms`, failures `0/92804`, dropped `27213` | Best Netty worker comparison run. |
| `swipes-fastpath-filter-14000-20260425-201652.log` | 14k/s | `7048.289341/s`, p95 `407.5ms`, failures `0/74687`, dropped `56645` | Saturated; k6 target not reached. |
| `swipes-fastpath-filter-16000-20260425-201825.log` | 16k/s | `8078.859124/s`, p95 `442.37ms`, failures `0/81737`, dropped `78272` | Saturated; k6 target not reached. |
| `swipes-netty-w8-12000-20260426-112922.log` | 12k/s | `7133.317898/s`, p95 `335.26ms`, failures `0/73273`, dropped `46797` | After user revert; no active Netty worker override in compose. |

Interpretation:

1. The Java service can show zero HTTP failures well beyond its sustainable throughput because accepted requests only enqueue locally.
2. The practical limit is visible through achieved `http_reqs/s`, dropped iterations, p95/p99 latency, CPU saturation, and Kafka/producer queue behavior.
3. Historical "failures" at 12k/14k/16k were usually k6 threshold and dropped-iteration failures, not HTTP 5xx/4xx failures.
4. A Go rewrite should be judged against the best clean-condition Java runs, not against a run made after the environment was left noisy or the topic was not reset.
