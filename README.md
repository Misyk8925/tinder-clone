# Tinder Clone — Microservices Architecture

A simplified microservices-based "Tinder-like" application built with Spring Boot 3.5.x and Spring Cloud 2025.0.x. The system manages user profiles, matching decisions (swipes), and generates ranked candidate decks cached in Redis.

## 🏗️ Architecture Overview

This is a distributed system comprising multiple Spring Boot services that communicate via HTTP and service discovery:

```
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway (Port 8222)                  │
│              Spring Cloud Gateway + Resilience4j             │
└────────────────┬────────────────────────────────────────────┘
                 │
    ┌────────────┼────────────┬──────────────┐
    ▼            ▼            ▼              ▼
┌────────┐  ┌────────┐  ┌────────┐  ┌──────────┐
│Profile │  │ Swipes │  │ Deck   │  │ Profiles │
│Service │  │Service │  │Service │  │ Discovery│
│(8010)  │  │ (8020) │  │(8030)  │  │ (8761)   │
└────────┘  └────────┘  └────────┘  └──────────┘
    │            │          │
    └────────────┴──────────┘
          │
    ┌─────▼─────┐
    │   Redis   │
    │ (6379)    │
    └───────────┘
```

### Services

| Service | Port | Purpose | Stack |
|---------|------|---------|-------|
| **Config Server** | 8888 | Centralized configuration management | Spring Cloud Config Server |
| **Discovery (Eureka)** | 8761 | Service registry and service discovery | Spring Cloud Netflix Eureka |
| **API Gateway** | 8222 | Request routing, circuit-breaking, cross-cutting policies | Spring Cloud Gateway + WebFlux |
| **Profiles Service** | 8010 | User profile CRUD, search, media management, caching | Spring MVC + JPA + PostgreSQL + Redis |
| **Swipes Service** | 8020 | Persistence of left/right swipe decisions | Spring MVC + JPA + PostgreSQL |
| **Deck Service** | 8030 | Ranked candidate deck generation and Redis cache management | Spring WebFlux + Reactive Redis |

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL (via Docker)
- Redis 8.2.1+ (via Docker)

### 1. Start Infrastructure

```bash
# Start Redis and other required services
docker-compose up -d
```

### 2. Build All Services

```bash
cd services

# Build each service
cd config-server2 && mvn clean install && cd ..
cd discovery && mvn clean install && cd ..
cd gateway && mvn clean install && cd ..
cd profiles && mvn clean install && cd ..
cd swipes && mvn clean install && cd ..
cd deck && mvn clean install && cd ..
```

### 3. Start Services (in order)

```bash
# Terminal 1: Config Server
cd services/config-server2
mvn spring-boot:run

# Terminal 2: Discovery (Eureka)
cd services/discovery
mvn spring-boot:run

# Terminal 3: Profiles Service
cd services/profiles
mvn spring-boot:run

# Terminal 4: Swipes Service
cd services/swipes
mvn spring-boot:run

# Terminal 5: Deck Service
cd services/deck
mvn spring-boot:run

# Terminal 6: API Gateway
cd services/gateway
mvn spring-boot:run
```

Services will auto-register with Eureka and become available through the gateway.

## 📋 Service Details

### Config Server (services/config-server2)

Provides centralized configuration management via Spring Cloud Config Server.

- **Port**: 8888
- **Profile**: native (reads from classpath:/configurations)
- **Role**: Configuration distribution to other services

### Discovery - Eureka (services/discovery)

Service registry enabling dynamic service discovery across the microservices architecture.

- **Port**: 8761
- **Features**: 
  - Service registration
  - Health checking
  - Client-side load balancing

### API Gateway (services/gateway)

Edge service that routes external requests to internal services with cross-cutting concerns.

- **Port**: 8222
- **Features**:
  - Request routing to downstream services
  - OAuth2 resource server (JWT validation)
  - Keycloak integration (JWK URL: `http://localhost:9080/realms/spring/protocol/openid-connect/certs`)
  - Resilience4j circuit-breaking
  - Service discovery via Eureka

**Routes**:
- `/api/v1/profiles/**` → Profiles Service (8010)

### Profiles Service (services/profiles)

Manages user profiles, preferences, search functionality, and photo/media management.

- **Port**: 8010
- **Database**: PostgreSQL (Testcontainers in tests, manual setup for local dev)
- **Cache**: Redis (spring.cache.type=redis, cache name: `PROFILE_ENTITY_CACHE`)
- **Features**:
  - Profile CRUD operations
  - Full-text search with Spatial queries
  - Photo upload to AWS S3
  - Geocoding integration
  - OAuth2 resource server (JWT)
  - OpenAPI/Swagger documentation (docs/profile-service-api.yaml)

**Key Endpoints**:
```
GET    /api/v1/profiles/{id}              - Get profile by ID
POST   /api/v1/profiles                   - Create profile
PUT    /api/v1/profiles/{id}              - Update profile
PATCH  /api/v1/profiles/{id}              - Partial update
DELETE /api/v1/profiles/{id}              - Soft delete
DELETE /api/v1/profiles?ids=...           - Bulk delete
GET    /api/v1/profiles/search            - Search profiles
POST   /photos/upload                     - Upload profile photo (multipart)
GET    /photos/download?key=...           - Download photo
DELETE /photos/delete?key=...             - Delete photo
```

**Configuration**:
```yaml
app.s3.bucket: your-bucket-name
app.geocoding.api-key: your-api-key
spring.datasource.url: jdbc:postgresql://localhost:5432/profiles_db
spring.redis.host: localhost
spring.redis.port: 6379
```

### Swipes Service (services/swipes)

Persists and queries user swipe decisions (left/right/match).

- **Port**: 8020
- **Database**: PostgreSQL with composite key (profile1Id, profile2Id)
- **DDL**: Auto-create schema
- **Features**:
  - Swipe persistence
  - Batch lookup of prior swipes
  - OAuth2 resource server (JWT)

**Key Endpoints**:
```
POST   /swipe                    - Record swipe decision
GET    /swipe/between/batch      - Check prior swipes (batch lookup)
```

**Request/Response**:
```json
POST /swipe
{
  "profile1Id": "uuid",
  "profile2Id": "uuid",
  "decision": true
}

GET /swipe/between/batch?viewer=uuid&candidates=uuid1,uuid2,uuid3
Response: {"uuid1": true, "uuid2": false, "uuid3": true}
```

### Deck Service (services/deck)

Generates ranked candidate "decks" and caches them in Redis for high-performance retrieval.

- **Port**: 8030 (if exposed)
- **Stack**: Spring WebFlux + Reactive Redis
- **Features**:
  - Reactive, non-blocking processing
  - Candidate search, filtering, and scoring
  - Redis ZSET cache for ranked decks
  - Keycloak token client (client_credentials grant)
  - Background scheduler for bulk deck rebuilds

**Deck Generation Pipeline**:
1. Search for candidates via Profiles Service
2. Filter out prior swipes via Swipes Service (batches of 200)
3. Score candidates (parallelized)
4. Sort by score descending
5. Cache top-N in Redis ZSET (TTL: configurable)

**Configuration**:
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

**Admin Endpoints** (`/api/v1/admin/deck`):
```
GET    /manual-rebuild?viewerId=uuid      - Trigger immediate rebuild
GET    /exists?viewerId=uuid              - Check if deck exists
GET    /size?viewerId=uuid                - Get deck size
POST   /rebuild?viewerId=uuid             - Rebuild for user
DELETE /?viewerId=uuid                    - Invalidate user's deck
```

**Scheduler**:
- Cron: `0 */1 * * * *` (every minute, production tuning recommended)
- Fetches active users and rebuilds decks in background

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

Or use the docker-compose.yml in the repository.

### Redis

```bash
# Already configured in docker-compose.yml
docker-compose up -d redis
```

## 🔐 Authentication & Security

- **OAuth2 Resource Server**: All services validate JWT tokens from Keycloak
- **Keycloak Integration**: 
  - Gateway validates JWK from Keycloak realm
  - Deck Service uses client_credentials grant for inter-service communication
  - JWK URL: `http://localhost:9080/realms/spring/protocol/openid-connect/certs`

## 🧪 Testing

Each service includes unit and integration tests:

```bash
# Run tests for a specific service
cd services/deck
mvn test

# Run tests with coverage
mvn test jacoco:report
```

Key test infrastructure:
- **Testcontainers**: PostgreSQL and Redis containers for integration tests
- **Spring Test**: @SpringBootTest for full context testing
- **Reactor Test**: reactor-test for reactive stream testing

## 📚 API Documentation

### Profiles Service OpenAPI

OpenAPI/Swagger spec available at: `docs/profile-service-api.yaml`

View interactively:
```bash
# When Profiles Service is running:
http://localhost:8010/swagger-ui.html
```

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
curl -X GET "http://localhost:8030/api/v1/admin/deck/manual-rebuild"
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
curl -X POST "http://localhost:8222/swipe" \
  -H "Content-Type: application/json" \
  -d '{
    "profile1Id": "uuid1",
    "profile2Id": "uuid2",
    "decision": true
  }'
```

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

## 🚨 Troubleshooting

### Services not discovering each other
- Ensure Eureka server is running on port 8761
- Check that service names match in both application.yml and Eureka registration
- Verify network connectivity between services

### Redis connection errors
- Ensure Redis is running: `docker-compose up -d redis`
- Check Redis port (default 6379) is accessible
- Verify spring.redis.* configuration in application.yml

### PostgreSQL connection errors
- Ensure PostgreSQL is running and accessible
- Check database credentials in application.yml
- Verify database exists: `createdb profiles_db`, `createdb swipes_db`

### Keycloak token errors
- Verify Keycloak is running and accessible
- Check token-uri, client-id, and client-secret in configuration
- Validate JWT expiry and skew settings

## 📖 Additional Resources

- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)
- [Spring WebFlux Guide](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Keycloak Security](https://www.keycloak.org/)
- [Redis Documentation](https://redis.io/documentation)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

## 📄 License

This project is provided as-is for educational and development purposes.

## 👤 Author

Michael — December 2025

---

**Last Updated**: December 1, 2025

