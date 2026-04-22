# Tinder Clone — Microservices Architecture

A "Tinder-like" application built with **Spring Boot 3.x** and **Spring Cloud**, using HTTP, Kafka, and gRPC for inter-service communication.

---

## 📐 Architecture

![Architecture Diagram](docs/Screenshot%202026-03-09%20at%2021.17.26.png)



### Kafka Topics

| Topic | Producer | Consumer(s) |
|-------|----------|-------------|
| `swipe-created` | Swipe Service | Consumer Service |
| `profile.created` / `profile.updated` / `profile.deleted` | Profiles Service | Consumer Service, Swipe Service |
| `match.created` | Consumer Service (Outbox) | Match Service |

---

## 🗂️ Services

| Service | Port | gRPC | Stack |
|---------|------|------|-------|
| **Config Server** | 8888 | — | Spring Cloud Config |
| **Discovery (Eureka)** | 8761 | — | Spring Cloud Netflix Eureka |
| **API Gateway** | 8222 | — | Spring Cloud Gateway |
| **Profiles Service** | 8010 | 9010 | Spring MVC · JPA · PostgreSQL · Redis · S3 |
| **Swipes Demo** | 8040 | — | Spring WebFlux · Kafka |
| **Consumer Service** | — | — | Spring Boot · Kafka · PostgreSQL (Outbox) |
| **Match Service** | — | — | Spring Boot · Kafka · PostgreSQL |
| **Deck Service** | 8030 | — | Spring WebFlux · Reactive Redis |
| **Subscriptions Service** | 8095 | 9095 | Spring Boot · Stripe · gRPC |

---

## 🚀 Quick Start

For production-like local runs, use the root `docker-compose.yml` with env values from `.env.prod.example` (copy to your local env file and set real secrets).
Per-service DB credentials are expected: `PROFILES_DB_*`, `MATCH_DB_*`, `CONSUMER_DB_*`, `SUBSCRIPTIONS_DB_*`, `SWIPES_DB_*`.

### Prerequisites
- Java 21+, Maven 3.9+, Docker

### 1. Start Infrastructure
```bash
docker-compose up -d
# Kafka, Zookeeper, PostgreSQL, Redis, LocalStack (S3), ELK
```

### 2. Start Services (in order)
```bash
# Config → Eureka → Profiles → Deck → Swipes → Consumer → Match → Subscriptions → Gateway
(cd services/config-server2  && mvn spring-boot:run) &
(cd services/discovery       && mvn spring-boot:run) &
(cd services/profiles        && mvn spring-boot:run) &
(cd services/deck            && mvn spring-boot:run) &
(cd services/swipes-demo     && mvn spring-boot:run) &
(cd services/consumer        && mvn spring-boot:run) &
(cd services/match           && mvn spring-boot:run) &
(cd services/subscriptions   && mvn spring-boot:run) &
(cd services/gateway         && mvn spring-boot:run) &
```

### 3. Troubleshooting Docker Maven cache (`*.lastUpdated` errors)
If Docker build fails with errors like `FileNotFoundException ... .pom.lastUpdated`, clean the affected BuildKit Maven cache id and rebuild.

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
The Deck Service uses `client_credentials` for inter-service calls.  
After a Stripe payment, Subscriptions Service calls Profiles gRPC `UpdatePremiumUser` → assigns `USER_PREMIUM` role in Keycloak.

---

## 🗄️ Infrastructure (`docker-compose.yml`)

| Service | Port | Purpose |
|---------|------|---------|
| PostgreSQL (postgis/postgis:17-3.4) | 5435 (host) -> 5432 (container) | Main DB host for profiles/match/consumer/subscriptions/swipes |
| Keycloak PostgreSQL | 5432 | Keycloak DB |
| Keycloak | 9080 | Auth / JWT issuer |
| Redis 8.2 | 6379 | Deck cache + swipe existence |
| Kafka | internal only (`kafka:29092`) | Event streaming |
| Zookeeper | internal only | Kafka coordination |

> ELK services are present in `docker-compose.yml` but currently commented out.
> LocalStack is not part of the current compose stack.

---

## 📋 Key Endpoints

### Profiles Service `:8010`
```
POST   /api/v1/profiles                  - Create profile
GET    /api/v1/profiles/{id}             - Get profile
PUT    /api/v1/profiles                  - Update my profile
DELETE /api/v1/profiles                  - Delete my profile
GET    /api/v1/profiles/deck             - User deck view
GET    /api/v1/profiles/internal/active  - Internal active profiles for deck rebuild
POST   /api/v1/profiles/photos/upload    - Upload photo (S3, max 5)
```

### Swipes Demo `:8040`
```
POST   /api/v1/swipes               - Record swipe → Kafka
```

### Deck Service `:8030`
```
GET    /api/v1/admin/deck/manual-rebuild?viewerId=   - Rebuild deck
GET    /api/v1/admin/deck/size?viewerId=             - Deck size
DELETE /api/v1/admin/deck/?viewerId=                 - Invalidate cache
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
cd services/<service-name>
mvn test                  # unit + integration
mvn test jacoco:report    # with coverage
```

Testcontainers (PostgreSQL, Redis), EmbeddedKafka, reactor-test (`StepVerifier`).

---

## 🩺 Health

```bash
curl http://localhost:8222/actuator/health   # Gateway
open http://localhost:8761                   # Eureka
# Kibana is optional (ELK block is commented in docker-compose)
```

---

## 📖 Tech Stack

| | |
|-|-|
| Language | Java 21 |
| Framework | Spring Boot 3.x · Spring Cloud 2025.x |
| Messaging | Apache Kafka (Confluent 7.5) |
| Caching | Redis 8.2 |
| Database | PostgreSQL 16 |
| Auth | Keycloak (OAuth2 / JWT) |
| RPC | gRPC (Spring gRPC) |
| Reactive | Project Reactor · Spring WebFlux |
| Storage | AWS S3 / LocalStack |
| Payments | Stripe |
| Observability | ELK Stack |
| Resilience | Resilience4j |
| Testing | JUnit 5 · Testcontainers · Mockito |

---

## 📁 Structure

```
tinder-clone/
├── services/
│   ├── config-server2/   discovery/   gateway/
│   ├── profiles/         swipes-demo/ consumer/
│   ├── match/            deck/        subscriptions/
├── docs/                 # OpenAPI spec, diagrams
├── certs/                # mTLS certificates
└── docker-compose.yml
```

---

*Author: Michael · 2025–2026 | Swagger UI: `http://localhost:8010/swagger-ui.html`*
