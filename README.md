# Tinder Clone — Reliable Matching Platform

This project explores how a dating product can keep the matching journey responsive and correct as responsibilities are split across independently deployable services. It is not a technology showcase: the work is centered on dependable user-facing flows, explicit service boundaries, and recoverable asynchronous processing.

## Goals

- Deliver the core journey end to end: profile creation, location-aware discovery, swiping, mutual matches, chat, and premium upgrades.
- Keep deck reads fast without making a profile change or a missed cache entry a correctness problem.
- Make cross-service side effects durable and traceable instead of relying on best-effort calls.
- Protect public and internal interfaces differently, with identity-aware limits and mutual TLS where sensitive data crosses service boundaries.

## What has been achieved

- **CQRS matching deck:** a dedicated read service serves cached deck entries, asks the write side to build a missing deck, then hydrates the resulting profile IDs. Reverse indexes let profile changes invalidate only affected decks.
- **Reliable matching events:** swipe persistence and match creation are paired with transactional-outbox records. Batch publishers claim pending work, retry failures with backoff, and expose failed/dead-lettered outcomes instead of silently dropping events.
- **Security boundaries:** user requests are authenticated through the gateway and rate-limited by role; internal profile and swipe-history calls use separate mTLS endpoints where configured.
- **Product integrations:** the current system includes geocoded profile locations, media storage, real-time conversations, and payment-triggered premium-role updates.
- **Executable confidence:** service-level unit and integration coverage includes real Redis and database dependencies through Testcontainers, plus targeted deck-read orchestration tests.

---

## 📐 Architecture

![Architecture Diagram](docs/Screenshot%202026-03-09%20at%2021.17.26.png)

### Kafka Topics

| Topic | Producer | Consumer(s) |
|-------|----------|-------------|
| `swipe-created` | Swipes Service | Consumer Service |
| `profile.created` / `profile.updated` / `profile.deleted` | Profiles Service | Consumer Service, Deck Service |
| `match.created` | Consumer Service (Outbox) | Match Service |

---

## 🗂️ Responsibility map

| Area | Responsibility |
|------|----------------|
| **Gateway** | One client entry point; authenticates requests and applies role-aware rate limits. |
| **Profiles + Location** | Owns profile data, photos, preferences, and location resolution. |
| **Deck + Deck-Read** | Builds a ranked discovery deck asynchronously and serves its read model to the client. |
| **Swipes + Consumer** | Records decisions, detects reciprocal likes, and publishes durable swipe/match events. |
| **Match** | Owns conversations created from confirmed matches. |
| **Subscriptions** | Connects payment completion to premium entitlement changes. |
| **Contracts** | Keeps shared API DTOs and event schemas explicit between service boundaries. |

> Deck-Read and Swipes both default to port 8040 — that's fine, they run as separate containers/processes and are never both bound to the same host port at once.

---

## 🚀 Quick Start

### Prerequisites
- Java 21+, Maven 3.9+, Go 1.22+, Node/Angular CLI, Docker

### Local stack (recommended)
The root `docker-compose.yml` is the production-shaped stack; `docker-compose.local.yml` overlays it for local dev (repo-local self-signed mTLS certs, host-exposed Kafka/location ports, relaxed OIDC issuer check).

```bash
# 1. Generate local mTLS certs (writes to ./certs, password "changeit")
./certs/generate-docker-certs.sh

# 2. Copy env template and fill in secrets — Compose auto-loads ./.env
cp .env.prod.example .env

# 3. Bring up the full stack with the local overlay
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

This starts: PostgreSQL (PostGIS), Keycloak (+ its own Postgres), Redis, Kafka/Zookeeper, Nexus, and every backend service plus the Angular client behind the gateway.

Per-service DB credentials are required: `PROFILES_DB_*`, `MATCH_DB_*`, `CONSUMER_DB_*`, `SUBSCRIPTIONS_DB_*`, `SWIPES_DB_*`, `LOCATION_DB_*`.

### Running services individually (hybrid: infra in Docker, services on host)
```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d postgres keycloak-postgres keycloak redis zookeeper kafka

(cd services/config-server2  && mvn spring-boot:run) &   # optional, local-only
(cd services/discovery       && mvn spring-boot:run) &   # optional, local-only
(cd services/location-go     && go run ./cmd/location) &
(cd services/profiles        && mvn spring-boot:run) &
(cd services/deck            && mvn spring-boot:run) &
(cd services/deck-read       && mvn quarkus:dev) &
(cd services/swipes-demo     && mvn spring-boot:run) &
(cd services/consumer        && mvn spring-boot:run) &
(cd services/match           && mvn spring-boot:run) &
(cd services/subscriptions   && mvn spring-boot:run) &
(cd services/gateway         && mvn spring-boot:run) &
(cd clients/tinder-client    && ng serve) &
```

Eureka/Config Server are legacy: in prod, every service resolves peers via static `*_SERVICE_URL` env vars (`EUREKA_CLIENT_ENABLED=false`). They're only useful for local dev without the compose overlay.

### Troubleshooting Docker Maven cache (`*.lastUpdated` errors)
If a Docker build fails with errors like `FileNotFoundException ... .pom.lastUpdated`, clean the affected BuildKit Maven cache id and rebuild.

```zsh
./scripts/clear-buildx-m2-cache.sh m2-swipes-demo
docker build --progress=plain -f services/swipes-demo/Dockerfile services/swipes-demo
```

If your `buildx` version does not support prune by id, use broader cleanup for cache mounts:

```zsh
./scripts/clear-buildx-m2-cache.sh all
```

---

## 🔐 Security

All services validate JWT tokens issued by **Keycloak** (`http://localhost:9080`, realm `spring`).
Service-to-service calls that carry sensitive data (Deck ⇄ Profiles internal, Deck-Read ⇄ Profiles internal, Deck ⇄ Consumer, Subscriptions gRPC) go over **mTLS** using per-service PKCS12 keystores and a shared truststore under `certs/` (local) or `/etc/dokploy/certs/tinderclone/` (prod).
After a Stripe payment, Subscriptions Service calls Profiles gRPC `UpdatePremiumUser` → assigns `USER_PREMIUM` role in Keycloak.
The Gateway enforces role-based rate limiting (`RoleBasedRateLimitFilter`) per route, differentiated by `anon` / `basic` / `premium` / `admin`.

---

## 🗄️ Infrastructure (`docker-compose.yml`)

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL (`postgis/postgis:17-3.4`) | 5435 (host) → 5432 (container) | Main DB host for profiles/match/consumer/subscriptions/swipes/location |
| Keycloak PostgreSQL | 5432 | Keycloak DB |
| Keycloak | 9080 | Auth / JWT issuer |
| Redis 8.2 | 6379 | Deck cache, deck-read cache, swipe existence cache |
| Kafka | internal only (`kafka:29092`); host-exposed on `9092` via the local overlay | Event streaming |
| Zookeeper | internal only | Kafka coordination |
| Nexus3 | 8081 | Maven artifact repository |

> ELK services are present in `docker-compose.yml` but fully commented out.
> Config Server and Discovery (Eureka) are also commented out in the prod compose — services resolve peers via static URLs instead.
> LocalStack is not part of the current compose stack.

---

## 📋 Key Endpoints

### Profiles Service `:8010`
```
GET    /api/v1/profiles/by-ids          - Batch fetch profiles
GET    /api/v1/profiles/{id}            - Get profile
GET    /api/v1/profiles/me              - Get my profile
POST   /api/v1/profiles                 - Create profile
PUT    /api/v1/profiles                 - Replace my profile
PATCH  /api/v1/profiles                 - Update my profile
DELETE /api/v1/profiles                 - Delete my profile
DELETE /api/v1/profiles/delete-many     - Bulk delete (admin)
```

### Profiles Service — internal `:8011` (mTLS only)
```
GET  /api/v1/profiles/internal/id-by-user/{userId}
GET  /api/v1/profiles/internal/search
GET  /api/v1/profiles/internal/by-ids
GET  /api/v1/profiles/internal/{id}
GET  /api/v1/profiles/internal/active   - Active profiles feed for deck rebuild
```

### Location Service `:8065`
```
GET  /health                            - Health check
# Geocoding + PostGIS-backed location resolution for profiles
```

### Deck-Read Service `:8040` (client-facing deck reads, Quarkus)
```
GET  /api/v1/deck                       - Get viewer's deck (viewer id from JWT sub)
```
The Gateway rewrites the legacy client path `GET /api/v1/profiles/deck` to `GET /api/v1/deck` on this service.

### Deck Service `:8030` (write side / admin)
```
GET    /api/v1/admin/deck/manual-rebuild?viewerId=   - Rebuild deck
GET    /api/v1/admin/deck/exists?viewerId=           - Deck existence check
GET    /api/v1/admin/deck/size?viewerId=             - Deck size
DELETE /api/v1/admin/deck?viewerId=                  - Invalidate cache
POST   /api/v1/internal/deck/ensure                  - Ensure-on-miss (called by Deck-Read)
```

### Swipes Service `:8040`
```
POST   /api/v1/swipes               - Record swipe → Kafka
POST   /api/v1/swipes/super         - Record super-like (premium/admin only) → Kafka
```

### Consumer Service `:8050` (8051 internal mTLS)
```
GET    /api/v1/swipes/liked-me      - Who liked me (premium/admin only)
```

### Match Service `:8080`
```
GET    /rest/conversations/**       - Conversation REST endpoints
GET    /ws, /ws/**                  - WebSocket chat
```

### Subscriptions Service `:8095`
```
POST   /api/v1/billing/checkout-session  - Create Stripe checkout session
POST   /api/v1/billing/portal-session    - Create Stripe portal session
POST   /api/v1/webhook                   - Stripe webhook (-> gRPC premium upgrade)
```

---

## 🧪 Testing

```bash
cd services/<java-service-name>
mvn test                  # unit + integration
mvn test jacoco:report    # with coverage

cd services/location-go   # or swipes-go
go test ./...
```

Testcontainers (PostgreSQL, Redis, Kafka), EmbeddedKafka, Quarkus Dev Services (deck-read), reactor-test (`StepVerifier`).

---

## 🩺 Health

```bash
curl http://localhost:8222/actuator/health   # Gateway
curl http://localhost:8040/q/health          # Deck-Read (Quarkus)
curl http://localhost:8065/health            # Location (Go)
open http://localhost:8761                   # Eureka (local dev only)
```

---

## 📁 Structure

```
tinder-clone/
├── services/
│   ├── config-server2/   discovery/     gateway/
│   ├── profiles/         location-go/
│   ├── deck/              deck-read/     swipes-demo/   swipes-go/
│   ├── consumer/         match/          subscriptions/
│   └── tinder-contracts/  # shared DTOs & Kafka event schemas
├── clients/
│   └── tinder-client/    # Angular frontend
├── docs/                 # OpenAPI spec, architecture notes, diagrams
├── certs/                # local mTLS cert generation
├── docker-compose.yml        # prod-shaped stack
├── docker-compose.local.yml  # local dev overlay
└── .env.prod.example
```

---

*Author: Michael · 2025–2026 | Swagger UI: `http://localhost:8010/swagger-ui.html`*
