# Backend Architecture Overview — Coding Agent Reference Prompt

You are working on a **Tinder-clone** microservices backend. This document describes the full system architecture, all services, their APIs, data models, connection types, caching, database indexes, and cross-service communication. Use this as your primary context when writing or modifying backend code.

---

## INFRASTRUCTURE

### PostgreSQL
- Single PostgreSQL 17 instance with PostGIS enabled
- 5 isolated databases, one per service, with separate DB users and passwords
- Each service connects via `jdbc:postgresql://postgres:5432/{service}_db`
- Connection pool: HikariCP (profiles: max 150, consumer: max 4, others: default)
- DDL strategy: `update` in dev, `validate` in prod (migration-based)
- DB migrations live in `/migrations/migration/V1_{service}.sql`
- All services use Hibernate with Spring Data JPA repositories

### Kafka
- Confluent Kafka 7.5.0 with Zookeeper
- Bootstrap servers: `kafka:29092`
- Internal listener: `PLAINTEXT://kafka:29092`
- External listener: `PLAINTEXT://localhost:9092`
- All Kafka producers use an outbox pattern in Profiles and Consumer services to guarantee delivery
- Kafka group IDs are service-specific (e.g., `consumer-service-group`)

### Redis
- Single Redis 8.2.1 (Alpine) instance at `redis:6379`
- Max memory: 128MB, eviction policy: `allkeys-lru`
- Used by: Profiles (profile/preference caching), Consumer (profile presence cache), Deck (deck sorted sets, preference cache), Swipes-Demo (profile cache)
- Spring Cache abstraction via `@Cacheable` with Redis backend
- No Redis Cluster — single node

### Keycloak
- Realm: `spring`
- JWK Set URI: `http://keycloak:9080/realms/spring/protocol/openid-connect/certs`
- All services act as OAuth2 Resource Servers, validating JWT tokens issued by Keycloak
- User ID is extracted from the JWT `sub` claim
- Roles are embedded in JWT claims: `admin`, `premium`, `basic`, `anonymous`

### mTLS Certificates
- Certificates stored at `/etc/dokploy/certs/tinderclone/` on the host
- Keystores: PKCS12 format (`{service}-service.p12`)
- Truststore: JKS format (`truststore.jks`)
- Password env vars: `MTLS_KEYSTORE_PASSWORD`, `MTLS_TRUSTSTORE_PASSWORD`
- Services exposing internal mTLS ports: Profiles (8011), Consumer (8051)
- Services acting as mTLS clients: Deck, Subscriptions (gRPC)

---

## SERVICE: PROFILES (port 8010, internal mTLS 8011, gRPC 9010)

### Purpose
Core profile management. Handles CRUD for profiles, photo uploads to S3, geospatial location, preferences, and publishes domain events via transactional outbox.

### REST API (public, JWT required)
- `GET /api/v1/profiles/me` — Authenticated user's profile
- `GET /api/v1/profiles/{id}` — Profile by ID
- `GET /api/v1/profiles/deck` — Paginated deck candidates (used by Deck service)
- `POST /api/v1/profiles` — Create profile
- `PUT /api/v1/profiles` — Full update
- `PATCH /api/v1/profiles` — Partial update
- `DELETE /api/v1/profiles` — Delete own profile
- `DELETE /api/v1/profiles/delete-many?ids=...` — Batch delete (admin)
- `POST /api/v1/profiles/{id}/photos` — Upload photos

### REST API (internal, mTLS port 8011, no JWT)
- `GET /api/v1/profiles/internal/{id}` — Profile by ID (called by Deck)
- `GET /api/v1/profiles/search` — Search profiles with preference-based filtering (called by Deck)

### gRPC API (port 9010, mTLS required)
- `UpdateUserPremiumStatus` — Updates `is_premium` flag (called by Subscriptions)

### Database: `profiles_db`

**Table: `profiles`**
- Columns: `id (UUID PK)`, `name`, `age`, `bio`, `city`, `gender`, `user_id (unique)`, `is_premium`, `created_at`, `deleted_at`, `updated_at`, `version`
- Soft deletes: `deleted_at` is set, never physically deleted
- Indexes: `age`, `gender`, `city`, `active_deleted (deleted_at)`, `search_query (composite)`, `created_at_deleted`, `active_created`, `name_lower`

**Table: `preferences`**
- Columns: `id (UUID PK)`, `profile_id (FK)`, `gender`, `min_age`, `max_age`, `max_range`
- One-to-one with `profiles`

**Table: `location`**
- Columns: `id (UUID PK)`, `profile_id (FK)`, `city`, `geo (GEOGRAPHY type via PostGIS)`, `created_at`, `updated_at`
- PostGIS geography column enables spatial distance queries
- One-to-one with `profiles`

**Table: `photo`**
- Columns: `photo_id (UUID PK)`, `profile_id (FK)`, `s3_key`, `url`, `is_primary`, `position`, `size`, `content_type`
- Many-to-one with `profiles`

**Table: `profile_hobbies`**
- Columns: `profile_id (FK)`, `hobby`
- ElementCollection mapped on Profile entity

**Table: `profile_event_outbox`**
- Columns: `id`, `profile_id`, `event_type`, `payload`, `publish_at`, `published_at`, `retries`, `max_retries`, `status`
- Indexes: `publish_window (publish_at + status)`, `profile_id`
- Scheduler polls this table and publishes to Kafka, then marks as published

### Caching (Redis)
- Profile data cached by profile ID
- Preference data cached
- Default TTL: 60 minutes
- Cache invalidated on profile update/delete

### Kafka (Producer)
- Topics: `profile.created`, `profile.updated`, `profile.deleted`
- Uses transactional outbox: events written to `profile_event_outbox` in the same DB transaction as the profile change
- Outbox scheduler publishes events to Kafka with retry and backoff

### External Integrations
- **AWS S3**: Photo storage. Bucket: `${AWS_S3_BUCKET}`. CloudFront CDN optional (`CDN_ENABLED`, `CLOUDFRONT_DOMAIN`). Presigned URL expiration: 300s. Image processing with imgscalr before upload.
- **Nominatim Geocoding**: Reverse geocoding from coordinates to city names. Base URL: `https://nominatim.openstreetmap.org`. Timeout: 5000ms. Retries: 3 with 400ms exponential backoff. Country filter: `de,at,pl` (configurable).

### Resilience4j Configuration
- Circuit breaker `nominatimClient`: failure threshold 60%, slow call threshold 1500ms
- Circuit breaker `redisCache`: failure threshold 50%, slow call threshold 1000ms
- Circuit breaker `kafkaProducer`: failure threshold 60%, slow call threshold 3000ms
- Bulkhead `nominatimClient`: max 20 concurrent calls
- Bulkhead `redisCache`: max 500 concurrent calls

### Security
- JWT OAuth2 Resource Server
- mTLS on port 8011 — internal-only, Deck service connects here
- gRPC with mTLS on port 9010 — Subscriptions service connects here
- Input sanitization via OWASP Java HTML Sanitizer + JSoup on all user-submitted text (bio, hobbies, messages)
- Jakarta Bean Validation on all request DTOs

---

## SERVICE: CONSUMER (port 8050, internal mTLS 8051)

### Purpose
Stores swipe records, detects mutual matches, tracks who liked a user (premium feature), publishes match events. Acts as the authoritative store for swipe decisions.

### REST API (public, JWT required)
- `GET /api/v1/swipes/liked-me` — Get profiles that liked the user. Header: `X-User-Id`. Requires premium or admin role.

### REST API (internal, mTLS port 8051)
- `POST /between/batch?viewerId={id}` — Batch check: which profiles from a list have already been swiped on by `viewerId`. Called by Deck service. Requires client cert with CN = `deck-service`.

### Database: `consumer_db`

**Table: `swipe_records`**
- Columns: `profile1_id (UUID)`, `profile2_id (UUID)` — composite PK (lower UUID always goes in profile1_id for consistent ordering), `decision1`, `decision2`, `version`
- Indexes: `profile1_id`, `profile2_id`, `(profile1_id, decision1)`, `(profile2_id, decision2)`, `both_profiles (composite on both IDs)`
- Used for mutual match detection: when both `decision1` and `decision2` are LIKE

**Table: `pending_likes`**
- Columns: `id (UUID PK)`, `liker_profile_id`, `liked_user_id`, `liked_at`, `is_super`
- Indexes: `liked_user_id`, `liker_profile_id`
- Unique constraint on `(liked_user_id, liker_profile_id)`
- Backing store for the "liked me" feature (premium)

**Table: `swipe_event_outbox`**
- Columns: `id`, `swiper_id`, `swiped_id`, `event_type`, `payload`, `publish_at`, `published_at`, `retries`, `status`
- Indexes: `publish_window`, `swiper_id`, `swiped_id`
- Transactional outbox for publishing swipe events to Kafka

**Table: `match_event_outbox`**
- Columns: `id`, `profile1_id`, `profile2_id`, `payload`, `publish_at`, `published_at`, `retries`, `status`
- Indexes: `publish_window`, `profile1_id`, `profile2_id`
- Transactional outbox for publishing match events to Kafka

**Table: `profile_cache_model`**
- Columns: `profile_id (UUID PK)`, `user_id (unique)`, `exists`
- Denormalized cache of active profiles — used to validate swipe targets quickly without querying Profiles service

### Caching (Redis)
- Profile presence cache to avoid cross-service calls
- Swipe decision short-term caching

### Kafka
- **Consumes**:
  - `profile.created` — Updates `profile_cache_model`
  - `profile.deleted` — Removes profile from cache, cleans up swipe records
  - `match.created` — Internal event handling
- **Produces** (via outbox):
  - `swipe-created` — Raw swipe event immediately after recording
  - `swipe-saved` — Confirmation event after persistence
  - `match.created` — When mutual like detected

### Security
- Port 8051: mTLS with required client auth. Only clients presenting a certificate with CN = `deck-service` are accepted. Keystore: PKCS12. Truststore: JKS.
- Port 8050: JWT OAuth2, role check on `/api/v1/swipes/liked-me`
- `X-User-Id` header injected by Gateway, never trusted from client directly

### Configuration
- `outbox.publisher.batch-size: 50` — batch size for outbox polling
- `spring.datasource.hikari.maximum-pool-size: 4` — intentionally low, CPU-bound service

---

## SERVICE: MATCH (port 8080)

### Purpose
Manages match records and real-time messaging between matched users. Handles WebSocket connections for live chat, conversation creation, and media attachments in messages.

### REST API (JWT required)
- `GET /match/{profileId}` — All matches for a profile
- `POST /match` — Create match manually (stub / internal use)
- `GET /rest/conversations/{conversationId}` — Conversation with message history
- `POST /rest/conversations` — Create conversation

### WebSocket (STOMP over WebSocket)
- Endpoints: `GET /ws`, `GET /ws/**`
- JWT authentication via Bearer header or `?token=` query param
- Real-time message delivery via STOMP protocol
- Message types: `TEXT`, `AUDIO`, `VIDEO`, `IMAGE`

### Database: `match_db`

**Table: `matches`**
- Columns: `profile1_id (UUID)`, `profile2_id (UUID)` — composite PK, `status (ENUM: ACTIVE, INACTIVE, UNMATCHED)`, `created_at`, `matched_at`, `unmatched_at`, `version`

**Table: `match_chat_analytics`**
- Columns: composite PK `(profile1_id, profile2_id)`, `active_days`, `total_messages`, `first_message_at`, `first_reply_at`, `first_reply_latency_ms`, `last_message_at`, `audio_duration_ms_total`, `video_duration_ms_total`, `unmatched_at`, `version`
- Indexes: `matched_at`, `last_message_at`, `(profile1_id, last_message_at)`, `(profile2_id, last_message_at)`

**Table: `conversations`**
- Columns: `conversation_id (UUID PK)`, `participant1_id`, `participant2_id`, `status (ENUM: ACTIVE, INACTIVE, ARCHIVED)`
- Unique constraint: `(participant1_id, participant2_id)`

**Table: `messages`**
- Columns: `message_id (UUID PK)`, `conversation_id (FK)`, `sender_id`, `type (ENUM: TEXT, AUDIO, VIDEO)`, `text`, `client_message_id`, `created_at`, `updated_at`
- Unique constraint: `(sender_id, client_message_id)` — idempotency key, prevents duplicate messages from the same client

**Table: `message_attachments`**
- Columns: `attachment_id (UUID PK)`, `message_id (FK)`, `storage_key`, `url`, `mime_type`, `size_bytes`, `width`, `height`, `duration_ms`, `sha256`, `original_name`
- FK to `messages`

### Kafka (Consumer)
- `match.created` — Creates a Conversation record when a match is established

### AWS Integration
- S3 for message attachments (photos, videos)
- CloudFront CDN support
- Presigned URLs for attachment access (300s expiration)

### Security
- JWT OAuth2 Resource Server
- Custom `JwtAuthConverter` extracts user info from Keycloak JWT claims
- WebSocket connections authenticated via JWT

---

## SERVICE: DECK (port 8030)

### Purpose
Background service that builds and maintains personalized profile "decks" (ordered lists of candidate profiles to swipe on) per user. Caches decks in Redis and rebuilds them in response to Kafka events or on a schedule.

### REST API (admin only, enforced by Gateway)
- `GET /api/v1/admin/deck/exists?viewerId={id}` — Check if deck exists
- `GET /api/v1/admin/deck/size?viewerId={id}` — Get deck size
- `POST /api/v1/admin/deck/rebuild?viewerId={id}` — Force rebuild
- `DELETE /api/v1/admin/deck?viewerId={id}` — Invalidate deck
- `GET /api/v1/admin/deck/manual-rebuild` — Rebuild all decks

### Database
- Deck service has no persistent database of its own
- All state is in Redis (decks as sorted sets, preference groups as hashes)

### Caching (Redis)
- Decks stored as Redis Sorted Sets keyed by `viewerId`
- Deck TTL: 60 minutes (configurable via `deck.ttl-minutes`)
- Preference cache: Groups users with identical preferences into a single Profiles service query. TTL: 5 minutes. Can reduce N user queries to M distinct preference group queries (where M << N).
- Distributed locks for rebuild coordination: lock timeout 30s, max 3 retries with 100ms delay

### Kafka (Consumer)
- `profile.updated` — Triggers deck rebuild for users whose deck might include the updated profile
- `profile.deleted` — Removes deleted profile from all cached decks immediately
- `swipe-saved` — Removes swiped profile from deck cache for the swiper

### Service-to-Service Communication (mTLS)
- **→ Profiles service** (HTTPS, port 8011): Calls `GET /api/v1/profiles/internal` and `GET /api/v1/profiles/search` to fetch matching profiles. Timeout: 5000ms. Retries: 1. Uses WebFlux reactive HTTP client for parallelism.
- **→ Consumer service** (HTTPS, port 8051): Calls `POST /between/batch` to filter already-swiped profiles. Timeout: 5000ms. Retries: 1.
- Deck is the mTLS client: uses `deck-service.p12` keystore, `truststore.jks` truststore, CN = `deck-service`

### Rebuild Queue
- Two-tier priority queue: high-priority (profile events) and low-priority (scheduled)
- Processing ratio: 3 high-priority per 1 low-priority (weight ratio 3:1)
- Scheduler cron: every minute (`0 0/1 * * * *`)
- Max concurrent rebuilds: 50 (configurable via `scheduler.max-concurrent-rebuilds`)
- Parallelism per rebuild: 32 concurrent profile fetch requests (configurable via `deck.parallelism`)

### Configuration
- `deck.per-user-limit: 500` — Max profiles stored per deck
- `deck.search-limit: 2000` — Max profiles fetched from Profiles service per rebuild
- `deck.request-timeout-ms: 4000` — Timeout for individual profile fetch calls during rebuild

---

## SERVICE: GATEWAY (port 8222)

### Purpose
Single entry point for all client requests. Validates JWTs, enforces role-based rate limiting, injects user context headers, and routes requests to downstream services.

### Routes and Rate Limits

| Route | Downstream | Admin | Premium | Basic | Anonymous |
|-------|-----------|-------|---------|-------|-----------|
| `POST /api/v1/profiles` | profiles:8010 | unlimited | 50/hr | 5/hr | blocked |
| `PUT/PATCH /api/v1/profiles` | profiles:8010 | unlimited | configurable | configurable | blocked |
| `GET /api/v1/profiles/me` | profiles:8010 | unlimited | unlimited | unlimited | — |
| `GET /api/v1/profiles/{id}` | profiles:8010 | unlimited | configurable | configurable | — |
| `DELETE /api/v1/profiles` | profiles:8010 | unlimited | configurable | configurable | blocked |
| `POST /api/v1/swipes` | swipes:8040 | unlimited | configurable | configurable | blocked |
| `POST /api/v1/swipes/super` | swipes:8040 | unlimited | allowed | blocked | blocked |
| `GET /api/v1/swipes/liked-me` | consumer:8050 | allowed | allowed | blocked | blocked |
| `GET /api/v1/admin/deck/**` | deck:8030 | allowed | blocked | blocked | blocked |
| `GET/POST /ws/**` | match:8080 | unlimited | unlimited | unlimited | — |
| `GET/POST /rest/conversations/**` | match:8080 | unlimited | unlimited | unlimited | — |
| `GET/POST /match/**` | match:8080 | unlimited | unlimited | unlimited | — |
| `POST /api/v1/billing/**` | subscriptions:8095 | unlimited | configurable | configurable | blocked |
| `POST /api/v1/webhook/**` | subscriptions:8095 | unlimited | unlimited | unlimited | unlimited |

### Filters
- `RoleBasedRateLimitFilter` — Reads role from JWT, applies bucket4j-style rate limits per role per endpoint
- `PremiumOrAdminFilter` — Blocks non-premium, non-admin requests on protected routes
- `JwtAuthenticationFilter` — Validates JWT, injects `X-User-Id` header downstream
- All downstream services trust the `X-User-Id` header injected by the gateway

### Configuration
```
PROFILES_SERVICE_URL: http://profiles:8010
SWIPES_SERVICE_URL:   http://swipes:8040
CONSUMER_SERVICE_URL: http://consumer:8050
DECK_SERVICE_URL:     http://deck:8030
MATCH_SERVICE_URL:    http://match:8080
SUBSCRIPTIONS_SERVICE_URL: http://subscriptions:8095

HTTP client pool max-idle-time: 10s
HTTP client pool max-life-time: 60s
Response timeout: 30s
Connect timeout: 5000ms
```

### Security
- Spring Cloud Gateway WebFlux
- OAuth2 Resource Server — validates JWT against Keycloak JWK Set URI
- Never proxies raw tokens to downstream; uses `X-User-Id` header instead

---

## SERVICE: SUBSCRIPTIONS (port 8095)

### Purpose
Manages user subscription lifecycle and Stripe payment processing. Grants/revokes premium status by calling the Profiles service via gRPC.

### REST API (JWT required)
- `POST /api/v1/billing/checkout-session` — Creates Stripe Checkout session for the authenticated user
- `POST /api/v1/billing/portal-session` — Creates Stripe Customer Portal session (manage/cancel subscription)
- `POST /api/v1/webhook` — Stripe webhook receiver (no JWT, Stripe signature verified via HMAC-SHA256)

### Database: `subscriptions_db`

**Table: `billing_subscriptions`** (exact name may vary)
- Columns: `id (UUID PK)`, `user_id`, `stripe_customer_id`, `stripe_subscription_id`, `status (ENUM)`, `created_at`, `updated_at`
- Used to map Keycloak user IDs to Stripe customer IDs

### Kafka
- No Kafka integration — subscription status flows via gRPC to Profiles service

### Service-to-Service Communication
- **→ Profiles service (gRPC, port 9010, mTLS)**: Calls `UpdateUserPremiumStatus` to flip `is_premium` flag on the profile. Uses `subscriptions-service.p12` keystore. Address: `static://profiles:9010`.
- **→ Profiles service (HTTP fallback)**: `http://profiles:8010/api/profiles` — HTTP fallback for profile lookups

### Stripe Integration
- Creates and retrieves Checkout sessions for one-time or subscription payments
- Creates Billing Portal sessions for subscription self-management
- Webhook event types handled: `customer.subscription.created`, `customer.subscription.deleted`, `customer.subscription.updated`, `invoice.paid`, `invoice.payment_failed`
- Webhook signature verified with `STRIPE_WEBHOOK_SECRET` before processing
- Two-stage webhook processing: `StripeWebhookIngestService` (validates + routes) → `StripeWebhookProcessService` (business logic)

### Security
- JWT OAuth2 Resource Server — user ID always from JWT `sub` claim, never from request body
- Stripe webhook: no JWT, verified by HMAC-SHA256 signature header `Stripe-Signature`
- gRPC mTLS: requires client certificate, `ssl.client-auth: REQUIRE`

---

## SERVICE: SWIPES-DEMO (port 8040)

### Purpose
Handles user swipe actions (like/pass/super-like). Records swipe intent, publishes swipe events to Kafka for Consumer service to process. Maintains a local profile presence cache populated via Kafka events.

### REST API (JWT required)
- `POST /api/v1/swipes` — Submit a regular swipe (like or pass). Body: `{ swipedId, isLike, isSuper }`
- `POST /api/v1/swipes/super` — Submit a super like (routed only for premium/admin by Gateway)

### Database: `swipes_db`

**Table: `profile_cache`** (ProfileCacheModel entity)
- Columns: `profile_id (UUID PK)`, `user_id (unique)`
- Denormalized presence cache — used to validate that swipe targets exist before publishing event
- Populated by `profile.created` events, cleaned by `profile.deleted` events

### Caching (Redis)
- Profile cache backed by Redis (may overlap with `profile_cache` table — one is DB, one is Redis)

### Kafka
- **Produces**: `swipe-created` event with `{ swiperId, swipedId, decision, timestamp }`
- **Consumes**:
  - `profile.created` — Adds profile to local cache
  - `profile.deleted` — Removes profile from local cache

### Kafka Producer Configuration
- `acks: 1` — Leader acknowledgment only (performance-optimized)
- `batch-size: 131072` (128KB)
- `linger-ms: 20` — Wait up to 20ms to batch messages
- `buffer-memory: 67108864` (64MB)

### Service-to-Service Communication
- **→ Profiles service (HTTP)**: `http://profiles:8010/api/v1/profiles` — Fallback to verify profile existence if not in local cache

### Security
- JWT OAuth2 Resource Server — user ID from JWT
- Super-like access enforced at Gateway level, not in this service

---

## CROSS-SERVICE COMMUNICATION MATRIX

### Synchronous Calls

| Caller | Callee | Protocol | Port | Auth |
|--------|--------|----------|------|------|
| Gateway | Profiles | HTTP | 8010 | Forwards JWT |
| Gateway | Swipes-Demo | HTTP | 8040 | Forwards JWT |
| Gateway | Consumer | HTTP | 8050 | Forwards JWT |
| Gateway | Deck | HTTP | 8030 | Forwards JWT |
| Gateway | Match | HTTP/WS | 8080 | Forwards JWT |
| Gateway | Subscriptions | HTTP | 8095 | Forwards JWT |
| Deck | Profiles | HTTPS mTLS | 8011 | Client cert (CN=deck-service) |
| Deck | Consumer | HTTPS mTLS | 8051 | Client cert (CN=deck-service) |
| Subscriptions | Profiles | gRPC mTLS | 9010 | Client cert (CN=subscriptions-service) |
| Swipes-Demo | Profiles | HTTP | 8010 | No auth (internal network) |

### Asynchronous Events (Kafka)

| Producer | Topic | Consumers |
|----------|-------|-----------|
| Profiles | `profile.created` | Consumer, Deck, Swipes-Demo |
| Profiles | `profile.updated` | Deck |
| Profiles | `profile.deleted` | Consumer, Deck, Swipes-Demo |
| Swipes-Demo | `swipe-created` | Consumer |
| Consumer | `swipe-saved` | Deck |
| Consumer | `match.created` | Match |

---

## DATA FLOWS

### Swipe Flow (happy path)
1. Client sends `POST /api/v1/swipes` to Gateway
2. Gateway validates JWT, injects `X-User-Id`, routes to Swipes-Demo
3. Swipes-Demo validates swiped profile exists in local cache
4. Swipes-Demo publishes `swipe-created` to Kafka
5. Consumer receives event, writes `SwipeRecord` to DB in a transaction with `swipe_event_outbox` entry
6. Outbox scheduler publishes `swipe-saved` to Kafka
7. Deck receives `swipe-saved`, removes swiped profile from viewer's Redis deck
8. Consumer detects mutual like → writes to `match_event_outbox` in same transaction
9. Outbox scheduler publishes `match.created` to Kafka
10. Match service receives `match.created`, creates `Conversation` record

### Profile Creation Flow
1. Client sends `POST /api/v1/profiles` → Gateway → Profiles
2. Profiles creates `Profile`, `Preferences`, `Location` records atomically
3. In same transaction, inserts `profile.created` event into `profile_event_outbox`
4. Outbox scheduler publishes event to `profile.created` Kafka topic
5. Consumer receives event → inserts into `profile_cache_model`
6. Swipes-Demo receives event → inserts into `profile_cache`
7. Deck receives event → optionally triggers deck rebuilds for users whose preferences match

### Premium Subscription Flow
1. Client sends `POST /api/v1/billing/checkout-session` → Gateway → Subscriptions
2. Subscriptions creates Stripe Checkout session, returns URL to client
3. Client completes payment on Stripe hosted page
4. Stripe calls `POST /api/v1/webhook` on Subscriptions service (bypasses JWT)
5. Subscriptions verifies Stripe signature, routes event by type
6. On `customer.subscription.created` or `invoice.paid`: calls Profiles via gRPC to set `is_premium = true`
7. Profiles updates DB and publishes `profile.updated` event via outbox
8. Deck receives `profile.updated` → rebuilds affected decks

---

## OUTBOX PATTERN DETAILS

Both Profiles and Consumer use the transactional outbox pattern:

1. Domain operation (create/update/delete) and outbox insert happen in a single DB transaction
2. A dedicated scheduler polls the outbox table for unpublished events (filtered by `publish_at <= now AND status = PENDING`)
3. Events are published to Kafka in batches (Consumer: batch size 50)
4. On successful publish, `published_at` is set and `status = PUBLISHED`
5. On failure, `retries` is incremented; after `max_retries`, status = `FAILED` (dead-letter equivalent)
6. Outbox rows are indexed on `(publish_at, status)` for efficient polling

---

## OBSERVABILITY

### Distributed Tracing
- Micrometer Tracing with Zipkin exporter
- `traceId` and `spanId` propagated in all HTTP and Kafka headers
- MDC (Mapped Diagnostic Context) populated with trace context for structured logging
- Sampling: 100% in development (reduce for production)
- Zipkin endpoint: `http://localhost:9411` (configurable via `management.zipkin.tracing.endpoint`)

### Metrics
- Micrometer metrics exported in Prometheus format at `/actuator/prometheus`
- Custom metrics for deck rebuild latency, cache hit/miss rates, Kafka lag

### Logging
- Logstash Logback Encoder for structured JSON logs
- All services output JSON-formatted logs with traceId, spanId, service name

### Health Checks
- All services expose `GET /actuator/health`
- Docker Compose health check: polls `/actuator/health` every 15s
- Circuit breaker state exposed via Actuator metrics

---

## ENVIRONMENT VARIABLES

### Database
- `POSTGRES_USER`, `POSTGRES_PASSWORD` — Superuser credentials
- `{SERVICE}_DB_USER`, `{SERVICE}_DB_PASSWORD` — Per-service credentials (PROFILES, MATCH, CONSUMER, SUBSCRIPTIONS, SWIPES)

### AWS / S3
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`, `AWS_REGION`
- `CLOUDFRONT_DOMAIN`, `CDN_ENABLED`

### Keycloak
- `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_POSTGRES_USER`, `KEYCLOAK_POSTGRES_PASSWORD`, `KEYCLOAK_POSTGRES_DB`
- `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET`
- `KEYCLOAK_JWK_SET_URI`

### mTLS
- `MTLS_KEYSTORE_PASSWORD`, `MTLS_TRUSTSTORE_PASSWORD`
- `PROFILES_KEYSTORE_PATH`, `PROFILES_GRPC_KEYSTORE_PATH`
- `DECK_KEYSTORE_PATH`, `CONSUMER_KEYSTORE_PATH`
- `SUBSCRIPTIONS_GRPC_KEYSTORE_PATH`

### Stripe
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID`
- `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`

### Service URLs (used by Gateway)
- `PROFILES_SERVICE_URL`, `MATCH_SERVICE_URL`, `CONSUMER_SERVICE_URL`
- `DECK_SERVICE_URL`, `SUBSCRIPTIONS_SERVICE_URL`, `SWIPES_SERVICE_URL`

---

## DOCKER COMPOSE STARTUP ORDER

```
postgres (healthcheck: pg_isready)
  └→ keycloak (healthcheck: HTTP /health/ready)
  └→ profiles (depends: postgres, kafka, redis)
  └→ match (depends: postgres, kafka)
  └→ consumer (depends: postgres, kafka, redis)
  └→ subscriptions (depends: postgres, kafka)
  └→ swipes-demo (depends: postgres, kafka, redis)

kafka (healthcheck: TCP 29092, startPeriod: 240s)
  └→ profiles, match, consumer, deck, subscriptions, swipes-demo

redis (healthcheck: PING)
  └→ profiles, consumer, deck, swipes-demo, gateway

deck (depends: kafka, redis — no DB)
gateway (depends: redis, profiles)
```

### Resource Limits (Docker)
- `postgres`: 512MB RAM, 1 CPU
- `redis`: 128MB RAM, 0.35 CPU
- `kafka`: 768MB RAM, 1 CPU
- `profiles`: 640MB RAM, 1 CPU
- `consumer`: 256MB RAM, 0.6 CPU
- `match`: 512MB RAM, 0.8 CPU
- `deck`: 512MB RAM, 0.8 CPU
- `gateway`: 384MB RAM, 0.6 CPU
- `subscriptions`: 384MB RAM, 0.6 CPU
- `swipes-demo`: 256MB RAM, 0.6 CPU

---

## DISABLED / OPTIONAL SERVICES

- **Discovery Service** (`/services/discovery`): Eureka-based service discovery. Disabled in docker-compose. Services currently use static hostnames.
- **Config Server** (`/services/config-server2`): Spring Cloud Config Server. Disabled. Services use local `application.yaml` files.
- **ELK Stack** (elk network in docker-compose): Commented out. Services have Logstash encoder ready but ELK not running.

---

## KEY FILE LOCATIONS

- Database migrations: `/migrations/migration/V1_{service}.sql`
- Docker orchestration: `/docker-compose.yml`
- DB init script: `/docker/postgres/init-databases.sh`
- Service source: `/services/{service-name}/src/main/java/`
- Service config: `/services/{service-name}/src/main/resources/application.yaml`
- Proto definitions: `/services/profiles/src/main/proto/` (gRPC)
- API spec: `/docs/profile-service-api.yaml`

---

## NOTES FOR CODING AGENTS

1. **User identity**: All services trust `X-User-Id` header from Gateway or JWT `sub` claim. Never accept user ID from request body.
2. **Soft deletes**: Profiles are never physically deleted — `deleted_at` is set. All profile queries must filter on `deleted_at IS NULL`.
3. **Profile ID vs User ID**: `profiles.user_id` maps to the Keycloak `sub` claim. `profiles.id` is the internal UUID used everywhere else.
4. **Idempotency**: Consumer service swipe endpoint is idempotent via `SwipeRecord` unique composite PK. Message service is idempotent via `(sender_id, client_message_id)`.
5. **Event ordering**: Kafka does not guarantee cross-partition ordering. Profile and swipe events use profile ID as partition key for per-profile ordering.
6. **mTLS internal calls**: Deck→Profiles and Deck→Consumer are over HTTPS with mutual certificate authentication. The Profiles internal port (8011) and Consumer internal port (8051) are not exposed to clients.
7. **Transactional outbox**: Never publish Kafka events directly from domain logic — always write to the outbox table in the same transaction and let the scheduler publish.
8. **Premium gating**: Super-likes and "liked-me" are premium features enforced at the Gateway layer. Individual services should not re-implement this check unless adding defense-in-depth.
9. **Redis TTL**: All Redis keys should have explicit TTLs. Deck entries: 60 min. Preference cache: 5 min. Profile cache: 60 min. Never leave keys without expiry.
10. **PostGIS**: Location-based search uses PostGIS geography type. Use `ST_DWithin` for radius queries. The `location.geo` column is type `GEOGRAPHY`, not `GEOMETRY` — distances are in meters.
