# Tinder Clone — Microservices Architecture

A "Tinder-like" application built with **Spring Boot 3.x** and **Spring Cloud**, using HTTP, Kafka, and gRPC for inter-service communication.

---

## 📐 Architecture

![Architecture Diagram](docs/Screenshot%202026-03-09%20at%2021.17.26.png)

```
                                          [S] Stripe          Keycloak
                                               ↑                  ↑
 ┌───────┐    ┌─────────────┐    ┌─────────────────────────────────────────────┐
 │ NGINX │───►│ API Gateway │    │                                             │
 └───────┘    └──────┬──────┘    │  ┌──────────────────┐                      │
                     │           │  │ subscription svc  │◄──── gRPC: premium   │
                     ├───────────►  └──────────────────┘                      │
                     │           │                                             │
                     ├───────────►  ┌──────────────┐   swipe.created  ╔══════════════╗
                     │           │  │  swipe svc   │────────────────► ║ consumer svc ║──► Postgres
                     │           │  └──────────────┘                  ╚══════════════╝
                     │           │        │ profile.created                   │
                     │           │        │ profile.updated          match.created + swipe.created
                     │           │        │ profile.deleted                   │
                     │           │        ↓                                   ↓
                     ├───────────►  ┌──────────────┐   match.created  ┌──────────────┐
                     │           │  │  match svc   │◄──────────────── │  match svc   │
                     │           │  └──────┬───────┘                  └──────────────┘
                     │           │         │ Postgres
                     │           │
                     ├───────────►  ┌──────────────┐  ←── deck ───  ╔═══════╗
                     │           │  │ profile svc  │                 ║ Redis ║
                     │           │  └──────────────┘  ──── deck ──► ╚═══════╝
                     │           │        │ Postgres
                     │           │        │
                     │           │        │ HTTP GET /active-profiles
                     │           │        ↓
                     └───────────►  ┌──────────────┐
                                 │  │  deck svc    │
                                 │  └──────────────┘
                                 └─────────────────────────────────────────────┘
```

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
cd services/config-server2  && mvn spring-boot:run &
cd services/discovery        && mvn spring-boot:run &
cd services/profiles         && mvn spring-boot:run &
cd services/deck             && mvn spring-boot:run &
cd services/swipes-demo      && mvn spring-boot:run &
cd services/consumer         && mvn spring-boot:run &
cd services/match            && mvn spring-boot:run &
cd services/subscriptions    && mvn spring-boot:run &
cd services/gateway          && mvn spring-boot:run &
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
| PostgreSQL 16 | 5432 | Profiles DB |
| Redis 8.2 | 6379 | Deck cache + swipe existence |
| Kafka | 9092 | Event streaming |
| Zookeeper | 2181 | Kafka coordination |
| LocalStack | 4566 | S3 emulation |
| Elasticsearch | 9200 | Logs |
| Logstash | 5010 | Log ingestion |
| Kibana | 5601 | Log UI |

> Consumer / Swipes services use a secondary PostgreSQL on port `54322`.  
> `docker run --name pg-swipes -e POSTGRES_PASSWORD=postgres -p 54322:5432 -d postgres:16`

---

## 📋 Key Endpoints

### Profiles Service `:8010`
```
POST   /api/v1/profiles             - Create profile
GET    /api/v1/profiles/{id}        - Get profile (Redis-cached)
PUT    /api/v1/profiles/{id}        - Update
DELETE /api/v1/profiles/{id}        - Soft delete
GET    /api/v1/profiles/active      - Used by Deck Service
POST   /photos/upload               - Upload photo (S3, max 5)
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
POST   /checkout                    - Create Stripe session
POST   /webhook                     - Stripe webhook (→ gRPC premium upgrade)
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
open http://localhost:5601                   # Kibana
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
