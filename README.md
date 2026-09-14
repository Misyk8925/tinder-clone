# Lunari — Reliable Matching Platform

[![Policy](https://github.com/Misyk8925/tinder-clone/actions/workflows/policy.yml/badge.svg)](https://github.com/Misyk8925/tinder-clone/actions/workflows/policy.yml)
[![Security](https://github.com/Misyk8925/tinder-clone/actions/workflows/security.yml/badge.svg)](https://github.com/Misyk8925/tinder-clone/actions/workflows/security.yml)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6-6DB33F?logo=springboot&logoColor=white)
![Quarkus](https://img.shields.io/badge/Quarkus-Deck_Read-4695EB?logo=quarkus&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Kafka-outbox-231F20?logo=apachekafka&logoColor=white)

A dating matching product that keeps discovery fast and match events correct when write, read, and side effects live in different services. The repository name is historical; the deployed product is Lunari. The client is a **phone-first PWA** (bottom tabs below 768px); desktop keeps a sidebar.

<p align="center">
  <img src="docs/demo/screenshots/mobile/discover.png" width="180" alt="Discover on a phone: full-screen card and bottom tabs" />
  <img src="docs/demo/screenshots/mobile/matches.png" width="180" alt="Matches on a phone" />
  <img src="docs/demo/screenshots/mobile/chat.png" width="180" alt="Chat on a phone" />
  <img src="docs/demo/screenshots/mobile/profile.png" width="180" alt="Profile on a phone" />
</p>

**Stack:** Java 21, Spring Boot, Quarkus, Kafka, Redis, PostgreSQL/PostGIS, Keycloak; location and swipe-write in Go; photos in FastAPI; Angular client.

**Six-minute path:** two prepared accounts → Discover (`GET /api/v2/deck`) → mutual like → match → text chat.

| Surface | Where |
|---|---|
| Live stand | https://lunari.misyk.tech — only after the [live-stand preflight](docs/demo/live-stand.md) is green that day |
| Identity | https://auth.misyk.tech · realm `spring` |
| Backup recording | Design-preview UI walkthrough is attached to the PR (Discover → Likes → Messages → Chat → Profile). Script: [docs/demo/script.md](docs/demo/script.md). Before sending the repo to recruiters, record the live origin and paste an unlisted YouTube/Loom URL here. |
| Interview kit | [docs/demo](docs/demo/README.md) |
| Local fallback | `docker-compose.yml` + `docker-compose.local.yml` |

Probed 2026-09-14: both public hosts returned Cloudflare **522** (origin down). Do not send the live URL while that is true.

## What I would explain in an interview

GitHub tracks these as **stories** (issue template: `.github/ISSUE_TEMPLATE/story.yml`):

1. **[#35 CQRS deck](https://github.com/Misyk8925/tinder-clone/issues/35).** `services/deck` builds and invalidates order. `services/deck-read` serves the read model and, on a miss, calls `ensure`. The Angular client reads **`/api/v2/deck`**. Boundary test: `DeckReadCqrsBoundaryAcceptanceTest`. Notes: [docs/demo/talk-track.md](docs/demo/talk-track.md), [docs/features/deck-read-cqrs](docs/features/deck-read-cqrs/README.md).
2. **[#36 Transactional outbox](https://github.com/Misyk8925/tinder-clone/issues/36).** Profile changes and swipe/match events are committed with an outbox row, then a batch publisher retries and dead-letters. Start at `ProfileOutboxBatchProcessor` and `SwipeOutboxEventDispatcher`.
3. **[#37 Security boundaries](https://github.com/Misyk8925/tinder-clone/issues/37).** Gateway JWT plus `RoleBasedRateLimitFilter` (per route and role). Internal profile and swipe-history calls use mTLS; Compose mounts are checked in CI (`scripts/validate-compose-mtls-mounts.rb`).

Full write-ups: [docs/demo/stories.md](docs/demo/stories.md).

## Current scope / not in the demo

- **In the demo:** profile, location-aware Discover, swipe, match, text chat. Premium / likes-you only if the account already has `USER_PREMIUM`.
- **In the repo, not claimed as finished:** ranking admin / experiments (`ADMIN` vs `USER_ADMIN` still open), popularity ranker on a live deck, moderation Phase 5.
- **Legacy, off in production:** Eureka and Config Server. Services resolve peers with static `*_SERVICE_URL`.
- **Do not SQL-seed profiles.** Missing `profile.created` leaves Deck Read at `202 BUILDING`. See [docs/demo/seed-notes.md](docs/demo/seed-notes.md).

CI watches security (CodeQL, Trivy, dependency-review) and policy/contracts (Redis, DB roles, mTLS mounts, Deck Read specs). It does **not** run `mvn test` / `go test` / `pytest` / `ng build`. Those suites run locally, often with Testcontainers.

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

## Architecture

![Architecture Diagram](docs/Screenshot%202026-03-09%20at%2021.17.26.png)

The browser talks only to the gateway. Discover is a read-model problem (Deck Read), not a live join across Profiles and swipe history. Deck rebuilds and reverse-index invalidation stay on the write side so a cache miss can call `ensure` instead of becoming a correctness bug. Swipe persistence and match creation are paired with outbox rows so a Kafka blip does not drop a mutual like. Photos, location, and billing are separate because they fail and scale differently. User JWTs never replace mTLS on internal profile-id fanout.

### Kafka Topics

| Topic | Producer | Consumer(s) |
|-------|----------|-------------|
| `swipe-created` | Swipes Service (`swipes-go` in Compose) | Consumer Service |
| `profile.created` / `profile.updated` / `profile.deleted` | Profiles Service (outbox) | Consumer Service, Deck Service, Deck Read |
| `match.created` | Consumer Service (outbox) | Match Service |

---

## Responsibility map

| Area | Responsibility |
|------|----------------|
| **Gateway** | One client entry point; authenticates requests and applies role-aware rate limits. |
| **Profiles + Location + Photos** | Owns profile data, preferences, and location resolution. Photo bytes, variants and S3 storage live in the Photos service. |
| **Photos** | Validates uploads, renders JPEG variants, stores objects in S3, and issues download URLs. Used by Profiles and Match. |
| **Deck + Deck-Read** | Builds a ranked discovery deck asynchronously and serves its read model to the client. |
| **Swipes + Consumer** | Records decisions (`swipes-go` on the Compose path), detects reciprocal likes, and publishes durable swipe/match events. |
| **Match** | Owns conversations created from confirmed matches. |
| **Subscriptions** | Connects payment completion to premium entitlement changes. |
| **Contracts** | Keeps shared API DTOs and event schemas explicit between service boundaries. |

> Deck-Read and Swipes both default to port 8040 — that is fine, they run as separate containers and are never bound to the same host port at once.

---

## Quick Start

### Prerequisites
- Java 21+, Maven 3.9+, Go 1.22+, Node/Angular CLI, Docker

### Local stack (recommended)
The root `docker-compose.yml` is the production-shaped stack; `docker-compose.local.yml` overlays it for local dev (repo-local self-signed mTLS certs, host-exposed Kafka/location ports, relaxed OIDC issuer check). Compose starts **swipes-go**, not the Java `swipes-demo` service.

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

After a `--no-deps` recreate of `deck-read-api`, restart `gateway` or Discover returns 500 (stale Netty DNS).

### Running services individually (hybrid: infra in Docker, services on host)

Prefer the full Compose stack for a demo. Hybrid is for local debugging only. Production-shaped swipe write is **swipes-go**. `swipes-demo` is the Java rollback overlay (`docker-compose.swipes-java-rollback.yml`).

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d postgres keycloak-postgres keycloak redis zookeeper kafka

(cd services/location-go     && go run ./cmd/location) &
(cd services/photos          && uvicorn app.main:app --host 0.0.0.0 --port 8070) &
(cd services/profiles        && mvn spring-boot:run) &
(cd services/deck            && mvn spring-boot:run) &
(cd services/deck-read       && mvn quarkus:dev) &
(cd services/swipes-go       && go run ./cmd/swipes-go) &
(cd services/consumer        && mvn spring-boot:run) &
(cd services/match           && mvn spring-boot:run) &
(cd services/subscriptions   && mvn spring-boot:run) &
(cd services/gateway         && mvn spring-boot:run) &
(cd clients/tinder-client    && npm start) &
```

`services/config-server2` and `services/discovery` are optional local-only leftovers. Production sets `EUREKA_CLIENT_ENABLED=false` and static `*_SERVICE_URL` values.

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

## Security

All services validate JWT tokens issued by **Keycloak** (`http://localhost:9080` locally, `https://auth.misyk.tech` in production, realm `spring`).
Service-to-service calls that carry sensitive data (Deck ⇄ Profiles internal, Deck-Read ⇄ Profiles internal, Deck ⇄ Consumer, Subscriptions gRPC) go over **mTLS** using per-service PKCS12 keystores and a shared truststore under `certs/` (local) or `/etc/dokploy/certs/tinderclone/` (prod).
After a Stripe payment, Subscriptions Service calls Profiles gRPC `UpdatePremiumUser` → assigns `USER_PREMIUM` role in Keycloak. Localhost cannot receive Stripe webhooks; use `POST /api/v1/billing/sync` there.
The Gateway enforces role-based rate limiting (`RoleBasedRateLimitFilter`) per route, differentiated by `anon` / `basic` / `premium` / `admin`.

---

## Infrastructure (`docker-compose.yml`)

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

## Key Endpoints

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

### Photos Service `:8070` (internal)
```
POST /api/v1/photos                     - Store an image and its JPEG variants
DELETE /api/v1/photos/{storageId}       - Delete all variants
GET  /api/v1/photos/{storageId}/download-url
POST /api/v1/photos/cleanup-orphaned
GET  /health
```
Profiles and Match call this service. Clients keep using the existing Profiles and Match photo endpoints.

### Deck-Read Service `:8040` (client-facing deck reads, Quarkus)
```
GET  /api/v1/deck                       - Viewer's deck (legacy path)
GET  /api/v2/deck                       - Viewer's deck (Angular client; JWT sub)
```
The Gateway routes both paths to this service. Prefer **v2** when talking about the product.

### Deck Service `:8030` (write side / admin)
```
GET    /api/v1/admin/deck/manual-rebuild?viewerId=   - Rebuild deck
GET    /api/v1/admin/deck/exists?viewerId=           - Deck existence check
GET    /api/v1/admin/deck/size?viewerId=             - Deck size
DELETE /api/v1/admin/deck?viewerId=                  - Invalidate cache
POST   /api/v1/internal/deck/ensure                  - Ensure-on-miss (called by Deck-Read)
```

### Swipes Service `:8040` (`swipes-go` in Compose)
```
POST   /api/v1/swipes               - Record swipe
POST   /api/v1/swipes/super         - Record super-like (premium/admin only)
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
POST   /api/v1/billing/sync              - Reconcile entitlement when webhooks cannot reach the host
POST   /api/v1/webhook                   - Stripe webhook (-> gRPC premium upgrade)
```

---

## Testing

```bash
cd services/<java-service-name>
mvn test                  # unit + integration
mvn test jacoco:report    # with coverage

cd services/location-go   # or swipes-go
go test ./...

cd services/photos
python -m pip install -r requirements-dev.txt
python -m pytest
```

Testcontainers (PostgreSQL, Redis, Kafka), EmbeddedKafka, Quarkus Dev Services (deck-read), reactor-test (`StepVerifier`).

GitHub Actions: [`.github/workflows/security.yml`](.github/workflows/security.yml) and [`.github/workflows/policy.yml`](.github/workflows/policy.yml) only. A green policy check is not a green service test suite.

---

## Health

```bash
curl http://localhost:8222/actuator/health   # Gateway
curl http://localhost:8040/q/health          # Deck-Read (Quarkus)
curl http://localhost:8065/health            # Location (Go)
```

---

## Structure

```
tinder-clone/
├── services/
│   ├── gateway/          profiles/       location-go/    photos/
│   ├── deck/             deck-read/      swipes-go/      swipes-demo/   # demo = Java rollback
│   ├── consumer/         match/          subscriptions/  moderation/
│   ├── tinder-contracts/ # shared DTOs & Kafka event schemas
│   ├── config-server2/   discovery/      # local-only leftovers
├── clients/
│   └── tinder-client/    # Angular frontend
├── docs/                 # demo kit, OpenAPI, architecture notes
│   └── demo/             # interview script, talk track, seed notes
├── certs/                # local mTLS cert generation
├── docker-compose.yml        # prod-shaped stack (swipes-go)
├── docker-compose.local.yml  # local dev overlay
└── .env.prod.example         # placeholders only; never commit a filled .env
```

---

*Author: Michael · 2025–2026 | Interview kit: [docs/demo](docs/demo/README.md)*
