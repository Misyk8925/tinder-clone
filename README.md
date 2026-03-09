# Tinder Clone — Microservices Architecture

> A simplified microservices-based "Tinder-like" application built with **Spring Boot 3.5.x** and **Spring Cloud 2025.0.x**. The system manages user profiles, matching decisions (swipes), and generates ranked candidate decks cached in Redis.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen?logo=springboot)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.x-brightgreen?logo=spring)
![Redis](https://img.shields.io/badge/Redis-8.2.1-red?logo=redis)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql)
![Keycloak](https://img.shields.io/badge/Keycloak-OAuth2%2FJWT-lightblue?logo=keycloak)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)

---

## 📑 Table of Contents

- [Architecture Overview](#️-architecture-overview)
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [Service Details](#-service-details)
  - [Config Server](#config-server-servicesconfig-server2)
  - [Discovery (Eureka)](#discovery-eureka-servicesdiscovery)
  - [API Gateway](#api-gateway-servicesgateway)
  - [Profiles Service](#profiles-service-servicesprofiles)
  - [Swipes Service](#swipes-service-servicesswipes)
  - [Deck Service](#deck-service-servicesdeck)
- [Database Setup](#️-database-setup)
- [Authentication & Security](#-authentication--security)
- [Testing](#-testing)
- [API Documentation](#-api-documentation)
- [Common Operations](#-common-operations)
- [Project Structure](#-project-structure)
- [Known Limitations](#-known-limitations)
- [Troubleshooting](#-troubleshooting)
- [Additional Resources](#-additional-resources)

---

## 🏗️ Architecture Overview

All external requests enter through the **API Gateway**, which routes them to the appropriate downstream service. Services register themselves with **Eureka** for dynamic discovery. The **Config Server** provides centralized configuration. **Redis** serves as a shared cache layer.

```
                    ┌──────────────────────────────────┐
                    │        Config Server (8888)       │
                    │     Spring Cloud Config Server    │
                    └────────────────┬─────────────────┘
                                     │ (config)
┌────────────────────────────────────▼─────────────────────────────────────┐
│                          API Gateway (Port 8222)                         │
│                   Spring Cloud Gateway + Resilience4j                    │
└───────────────────────────────────┬──────────────────────────────────────┘
                                    │
             ┌──────────────────────┼──────────────────────┐
             ▼                      ▼                      ▼
       ┌──────────┐           ┌──────────┐           ┌──────────┐
       │ Profiles │           │  Swipes  │           │   Deck   │
       │ Service  │           │ Service  │           │ Service  │
       │  (8010)  │           │  (8020)  │           │  (8030)  │
       └────┬─────┘           └────┬─────┘           └────┬─────┘
            │                      │                       │
            └──────────────────────┴───────────────────────┘
                                   │
                           ┌───────▼───────┐
                           │     Redis     │
                           │    (6379)     │
                           └───────────────┘

          ┌──────────────────────────────────────────┐
          │       Discovery / Eureka (8761)          │
          │    Spring Cloud Netflix Eureka Server    │
          └──────────────────────────────────────────┘
```

### Services at a Glance

| Service | Port | Purpose | Stack |
|---------|------|---------|-------|
| **Config Server** | 8888 | Centralized configuration management | Spring Cloud Config Server |
| **Discovery (Eureka)** | 8761 | Service registry and service discovery | Spring Cloud Netflix Eureka |
| **API Gateway** | 8222 | Request routing, circuit-breaking, JWT validation | Spring Cloud Gateway + WebFlux |
| **Profiles Service** | 8010 | User profile CRUD, search, photo/media management, caching | Spring MVC + JPA + PostgreSQL + Redis |
| **Swipes Service** | 8020 | Persistence of left/right swipe decisions | Spring MVC + JPA + PostgreSQL |
| **Deck Service** | 8030 | Ranked candidate deck generation and Redis cache management | Spring WebFlux + Reactive Redis |

---

## 🛠 Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.5.x, Spring Cloud 2025.0.x |
| **Service Discovery** | Spring Cloud Netflix Eureka |
| **API Gateway** | Spring Cloud Gateway (WebFlux) + Resilience4j |
| **Config Management** | Spring Cloud Config Server (native profile) |
| **Persistence** | Spring Data JPA + PostgreSQL 15 |
| **Caching** | Redis 8.2.1 (Spring Cache + Reactive Redis) |
| **Security** | OAuth2 Resource Server (JWT) + Keycloak |
| **Reactive Stack** | Spring WebFlux + Project Reactor |
| **Media Storage** | AWS S3 |
| **Geocoding** | External Geocoding API |
| **Testing** | JUnit 5, Testcontainers, Spring Test, Reactor Test, JaCoCo |
| **Build & Infra** | Maven 3.8+, Docker, Docker Compose |

---

## 🚀 Quick Start

### Prerequisites

- **Java 21+**
- **Maven 3.8+**
- **Docker & Docker Compose**

### 1. Start Infrastructure

Spin up PostgreSQL and Redis via Docker Compose:

```bash
docker-compose up -d
```

This starts:
- Redis on port **6379**
- PostgreSQL on port **5432** (if included in `docker-compose.yml`)

### 2. Build All Services

```bash
cd services
for svc in config-server2 discovery gateway profiles swipes deck; do
  cd $svc && mvn clean install -DskipTests && cd ..
done
```

Or build them individually:

```bash
cd services/config-server2 && mvn clean install && cd ../..
cd services/discovery      && mvn clean install && cd ../..
cd services/gateway        && mvn clean install && cd ../..
cd services/profiles       && mvn clean install && cd ../..
cd services/swipes         && mvn clean install && cd ../..
cd services/deck           && mvn clean install && cd ../..
```

### 3. Start Services (in order)

Services **must** be started in this order due to dependencies:

```bash
# 1. Config Server — must start first
cd services/config-server2 && mvn spring-boot:run

# 2. Discovery (Eureka) — other services register here
cd services/discovery && mvn spring-boot:run

# 3. Downstream services (any order after Eureka is up)
cd services/profiles && mvn spring-boot:run
cd services/swipes   && mvn spring-boot:run
cd services/deck     && mvn spring-boot:run

# 4. API Gateway — start last
cd services/gateway && mvn spring-boot:run
```

> Once started, all services auto-register with Eureka and become available through the gateway at `http://localhost:8222`.

---

## 📋 Service Details

### Config Server (`services/config-server2`)

Provides centralized configuration for all services via Spring Cloud Config Server.

| Property | Value |
|----------|-------|
| Port | `8888` |
| Profile | `native` (reads from `classpath:/configurations`) |
| App Name | `config-server` |

---

### Discovery / Eureka (`services/discovery`)

Service registry enabling dynamic service discovery.

| Property | Value |
|----------|-------|
| Port | `8761` |
| UI | `http://localhost:8761` |

Features: service registration, health checking, client-side load balancing.

---

### API Gateway (`services/gateway`)

Edge service routing all external traffic to internal microservices.

| Property | Value |
|----------|-------|
| Port | `8222` |
| Security | OAuth2 Resource Server (JWT via Keycloak) |
| Circuit Breaker | Resilience4j |

**Configured Routes:**

| Route | Target |
|-------|--------|
| `/api/v1/profiles/**` | Profiles Service (`:8010`) |

> ⚠️ Currently only the Profiles route is configured. Swipes and Deck routes can be added in `services/gateway/src/main/resources/application.yml`.

**Keycloak JWK URI:**
```
http://localhost:9080/realms/spring/protocol/openid-connect/certs
```

---

### Profiles Service (`services/profiles`)

Manages user profiles, preferences, geolocation search, and photo/media.

| Property | Value |
|----------|-------|
| Port | `8010` |
| Database | PostgreSQL (`profiles_db`) |
| Cache | Redis (`PROFILE_ENTITY_CACHE`) |
| Docs | `http://localhost:8010/swagger-ui.html` |

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/profiles/{id}` | Get profile by ID |
| `POST` | `/api/v1/profiles` | Create profile (returns `201` with `profileId`) |
| `PUT` | `/api/v1/profiles/{id}` | Full update (returns `404` if not found) |
| `PATCH` | `/api/v1/profiles/{id}` | Partial update (JSON merge) |
| `DELETE` | `/api/v1/profiles/{id}` | Soft delete (sets `deleted=true`, evicts cache) |
| `DELETE` | `/api/v1/profiles?ids=...` | Bulk soft delete |
| `GET` | `/api/v1/profiles/search` | Search profiles (full-text + spatial) |
| `POST` | `/photos/upload` | Upload photo (multipart, max 5 per user) |
| `GET` | `/photos/download?key=...` | Get download URL |
| `GET` | `/photos/get-photo-url?key=...` | Get pre-signed S3 URL |
| `DELETE` | `/photos/delete?key=...` | Delete photo |

**Configuration:**
```yaml
app:
  s3.bucket: your-bucket-name
  geocoding.api-key: your-api-key

spring:
  datasource.url: jdbc:postgresql://localhost:5432/profiles_db
  redis:
    host: localhost
    port: 6379
```

---

### Swipes Service (`services/swipes`)

Persists and queries user swipe decisions (like / pass).

| Property | Value |
|----------|-------|
| Port | `8020` |
| Database | PostgreSQL (`swipes_db`) |
| Key | Composite key: `(profile1Id, profile2Id)` |

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/swipe` | Record a swipe decision |
| `GET` | `/swipe/between/batch` | Batch lookup of prior swipes |

**Request / Response examples:**
```json
// POST /swipe
{
  "profile1Id": "11111111-1111-1111-1111-111111111111",
  "profile2Id": "22222222-2222-2222-2222-222222222222",
  "decision": true
}

// GET /swipe/between/batch?viewer=<uuid>&candidates=<uuid1>,<uuid2>
// Response:
{"<uuid1>": true, "<uuid2>": false}
```

---

### Deck Service (`services/deck`)

Generates ranked candidate "decks" and caches them in Redis for high-performance retrieval.

| Property | Value |
|----------|-------|
| Port | `8030` |
| Stack | Spring WebFlux + Reactive Redis |
| Cache | Redis ZSET (ranked, TTL-backed) |

**Deck Generation Pipeline:**

```
1. Search candidates  →  Profiles Service
2. Filter prior swipes  →  Swipes Service (batches of 200)
3. Score candidates  →  Parallelized scoring
4. Sort by score (desc)
5. Cache top-N in Redis ZSET (configurable TTL)
```

**Admin Endpoints** (`/api/v1/admin/deck`):

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/manual-rebuild?viewerId=<uuid>` | Trigger immediate rebuild |
| `GET` | `/exists?viewerId=<uuid>` | Check if deck exists in cache |
| `GET` | `/size?viewerId=<uuid>` | Get deck size |
| `POST` | `/rebuild?viewerId=<uuid>` | Rebuild deck for user |
| `DELETE` | `/?viewerId=<uuid>` | Invalidate user's deck cache |

**Configuration:**
```yaml
deck:
  parallelism: 32               # Parallel scoring threads
  request-timeout-ms: 5000      # WebClient timeout
  retries: 1                    # Retry attempts
  ttl-minutes: 60               # Redis cache TTL
  per-user-limit: 500           # Max candidates per deck
  search-limit: 2000            # Max candidates for scoring

keycloak:
  token-uri: http://localhost:9080/realms/spring/protocol/openid-connect/token
  client-id: deck-service
  client-secret: your-secret
  scope: openid profile
  clock-skew-seconds: 30
```

**Scheduler:**
- Cron: `0 */1 * * * *` (every minute; tune for production)
- Fetches active users and rebuilds their decks in the background

---

## 🗄️ Database Setup

### PostgreSQL (for Profiles and Swipes)

```bash
# Using Docker
docker run --name postgres-tinder \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_USER=postgres \
  -p 5432:5432 \
  -d postgres:15

# Create databases
docker exec postgres-tinder psql -U postgres -c "CREATE DATABASE profiles_db;"
docker exec postgres-tinder psql -U postgres -c "CREATE DATABASE swipes_db;"
```

Or use the `docker-compose.yml` in the repository root.

### Redis

```bash
# Already configured in docker-compose.yml
docker-compose up -d redis
```

---

## 🔐 Authentication & Security

- **OAuth2 Resource Server**: All services validate JWT tokens issued by Keycloak
- **Keycloak Integration**:
  - Gateway validates JWK from Keycloak realm
  - Deck Service uses `client_credentials` grant for inter-service communication
  - JWK URL: `http://localhost:9080/realms/spring/protocol/openid-connect/certs`

---

## 🧪 Testing

Each service includes unit and integration tests:

```bash
# Run tests for a specific service
cd services/deck
mvn test

# Run tests with coverage report
mvn test jacoco:report
```

Key test infrastructure:
- **Testcontainers**: Spins up PostgreSQL and Redis containers for integration tests
- **Spring Test**: `@SpringBootTest` for full application context testing
- **Reactor Test**: `reactor-test` for reactive stream testing

---

## 📚 API Documentation

OpenAPI/Swagger spec: `docs/profile-service-api.yaml`

View interactively when the Profiles Service is running:
```
http://localhost:8010/swagger-ui.html
```

---

## 🔧 Common Operations

### Check Service Health

```bash
# Via gateway
curl http://localhost:8222/actuator/health

# Directly
curl http://localhost:8010/actuator/health  # Profiles
curl http://localhost:8020/actuator/health  # Swipes
curl http://localhost:8030/actuator/health  # Deck
```

### View Service Registry (Eureka)

```
http://localhost:8761
```

### Manual Deck Rebuild

```bash
curl -X GET "http://localhost:8030/api/v1/admin/deck/manual-rebuild?viewerId=<uuid>"
```

### Create a Profile

```bash
curl -X POST "http://localhost:8222/api/v1/profiles" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "age": 28,
    "gender": "MALE"
  }'
```

### Record a Swipe

```bash
curl -X POST "http://localhost:8020/swipe" \
  -H "Content-Type: application/json" \
  -d '{
    "profile1Id": "11111111-1111-1111-1111-111111111111",
    "profile2Id": "22222222-2222-2222-2222-222222222222",
    "decision": true
  }'
```

---

## 📁 Project Structure

```
tinder-clone/
├── services/
│   ├── config-server2/          # Config Server
│   ├── discovery/               # Eureka Server
│   ├── gateway/                 # API Gateway
│   ├── profiles/                # Profiles Service
│   ├── swipes/                  # Swipes Service
│   └── deck/                    # Deck Service
├── docs/
│   └── profile-service-api.yaml # OpenAPI spec
├── docker-compose.yml           # Infrastructure setup
├── documentation.md             # Detailed documentation
└── README.md                    # This file
```

---

## ⚠️ Known Limitations

- **Deck Scheduler is empty**: `@EnableScheduling` is configured in the Deck service, but `DeckScheduler` contains no `@Scheduled` methods yet. Background deck rebuilds are not running automatically.
- **Gateway routes only Profiles**: Only `/api/v1/profiles/**` is currently routed through the gateway. Routes for Swipes and Deck must be added manually in `services/gateway/src/main/resources/application.yml`.
- **AWS S3 credentials required**: The Profiles service integrates with AWS S3 for photo storage. Valid AWS credentials (via environment variables, instance profile, or `~/.aws/credentials`) and a configured `app.s3.bucket` are required for photo operations to work.
- **Keycloak required for full auth**: JWT validation is enabled in the gateway and Profiles service. Running without a local Keycloak instance requires disabling or mocking the OAuth2 resource server configuration.
- **Swipes DB port**: The Swipes service default configuration targets PostgreSQL at `127.0.0.1:54322`. Adjust `services/swipes/src/main/resources/application.yml` if your PostgreSQL is on a different host/port.

---

## 🚨 Troubleshooting

### Services not discovering each other
- Ensure Eureka server is running on port `8761`
- Check that service names match in both `application.yml` and Eureka registration
- Verify network connectivity between services

### Redis connection errors
- Ensure Redis is running: `docker-compose up -d redis`
- Check Redis port (default `6379`) is accessible
- Verify `spring.redis.*` configuration in `application.yml`

### PostgreSQL connection errors
- Ensure PostgreSQL is running and accessible
- Check database credentials in `application.yml`
- Verify databases exist: `createdb profiles_db`, `createdb swipes_db`

### Keycloak token errors
- Verify Keycloak is running and accessible at `http://localhost:9080`
- Check `token-uri`, `client-id`, and `client-secret` in configuration
- Validate JWT expiry and clock-skew settings

---

## 📖 Additional Resources

- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Spring WebFlux Guide](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Keycloak Documentation](https://www.keycloak.org/)
- [Redis Documentation](https://redis.io/documentation)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

---

## 📄 License

This project is provided as-is for educational and development purposes.

## 👤 Author

Michael

---

**Last Updated**: 2026-03-09

