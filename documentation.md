# Tinder Clone — Project Documentation

Last updated: 2025-10-08 07:17 (local)

This repository contains a simplified microservices-based “Tinder-like” application. It is organized into multiple Spring Boot services that collaborate via HTTP and service discovery, with a gateway fronting downstream services. Some components are partially implemented (e.g., Deck service scheduling hooks), while others (Profiles, Swipes) provide functional endpoints.

High-level tech stack
- Java + Spring Boot
- Spring Cloud (Gateway, Config Server, Netflix Eureka)
- Spring Data JPA (Swipes)
- PostgreSQL (Swipes)
- Redis (Profiles caching)
- AWS S3 integration (Profiles photos)
- Docker Compose (for Redis)

Monorepo layout (key modules)
- services/config-server2 — Spring Cloud Config Server (native profile)
- services/discovery — Eureka service registry
- services/gateway — Spring Cloud Gateway
- services/profiles — Profiles management service (profiles, preferences, photos, geocoding)
- services/swipes — Swipes persistence service (records left/right decisions)
- services/deck — Deck service (scheduling/async scaffolding)

Architecture overview
- Config Server (port 8888) provides centralized configuration; the Profiles service imports its configuration from here (optional:configserver).
- Eureka Discovery (default port 8761) acts as a service registry. The gateway is configured to register and discover services.
- API Gateway (port 8222) exposes routes to backend services; currently routes /api/v1/profiles/** to the Profiles service at http://localhost:8010.
- Profiles service (expected at port 8010 as per gateway route) handles profile CRUD, caching via Redis, photo operations via S3, and security via OAuth2 resource server/JWT.
- Swipes service (port 8020) persists swipe decisions in PostgreSQL using a composite key for two profile IDs.
- Deck service enables Async and Scheduling (no concrete scheduled jobs yet).

Services in detail

1) Config Server (services/config-server2)
- Role: Centralized configuration server.
- Port: 8888 (see application.yml).
- Profile: native; configurations are read from classpath:/configurations.
- App name: config-server.

2) Discovery (services/discovery)
- Role: Eureka server (service registry).
- Port: Not explicitly configured here; typical default 8761.
- Code: @EnableEurekaServer in DiscoveryApplication.

3) Gateway (services/gateway)
- Role: Edge service routing external traffic to internal services.
- Port: 8222.
- Discovery: Configured to talk to Eureka at http://localhost:8761/eureka.
- Security: Configured as an OAuth2 resource server with JWT (Keycloak JWK URI at http://localhost:9080/realms/spring/protocol/openid-connect/certs).
- Routing (application.yml):
  - id: profiles → uri: http://localhost:8010, predicates: Path=/api/v1/profiles/**
  - Note: Only the Profiles route is defined in the provided config.

4) Profiles (services/profiles)
- Role: Manage user profiles and their media.
- Caching: Redis (spring.cache.type=redis). Redis host/port pulled from application.yml (localhost:6379). A docker-compose.yml is provided at repo root to run Redis.
- Security: OAuth2 Resource Server with JWT via Keycloak JWKs URL.
- Config Import: optional:configserver:http://localhost:8888
- App name: profiles-service
- S3 and external services:
  - S3 bucket name configured at app.s3.bucket
  - Geocoding client config under app.geocoding.*
- Selected REST endpoints:
  - Profiles (ProfileController at /api/v1/profiles)
    - GET /api/v1/profiles/{id} → GetProfileDto
    - POST /api/v1/profiles → Create a profile (CreateProfileDtoV1)
      - Returns 201 and ApiResponse with new profileId
      - 409 CONFLICT if a profile with the same username exists
    - PUT /api/v1/profiles/{id} → Update profile (returns updated entity)
      - 404 if entity not found
    - PATCH /api/v1/profiles/{id} → Partial update (JSON merge via ObjectMapper)
    - DELETE /api/v1/profiles/{id} → Soft delete (marks deleted=true), evicts cache
    - DELETE /api/v1/profiles?ids=<uuid>&ids=<uuid> → Bulk delete
    - GET /api/v1/profiles/test-pa → Test endpoint protected with @PreAuthorize("hasAnyRole('AMI')")
  - Photos (S3PhotoController at /photos)
    - POST /photos/upload (multipart form-data) with parts:
      - file: MultipartFile, key: String, optional userId
      - If userId provided: enforces per-user limit of 5 photos; uploads and persists metadata
      - Returns ApiResponse with stored key
    - GET /photos/download?key=... → Returns ApiResponse "Photo download URL generated" with a URL-like payload
    - GET /photos/get-photo-url?key=... → Returns pre-signed URL (from service)
    - DELETE /photos/delete?key=... → Deletes a photo; returns success ApiResponse
- Internals and caching:
  - ProfileServiceImpl uses a CacheManager-backed cache named PROFILE_ENTITY_CACHE to accelerate GETs by id. On create/update/delete it updates/evicts cache.
  - Preferences are saved separately if transient on create/update.

5) Swipes (services/swipes)
- Role: Persist swipe decisions between two profiles using a composite key.
- Port: 8020 (see application.yml).
- Database: PostgreSQL (DDL auto: create; URL: jdbc:postgresql://127.0.0.1:54322/postgres, username/password postgres/postgres).
- Logging: Hibernate SQL logging enabled for debugging.
- Main endpoint:
  - POST /swipe
    - Body (JSON):
      {
        "profile1Id": "<uuid-as-string>",
        "profile2Id": "<uuid-as-string>",
        "decision": true|false
      }
    - Behavior:
      - Compute embedded id = (profile1Id, profile2Id)
      - If no existing record: create SwipeRecord(decision1=decision, decision2=null)
      - If existing record and decision2 is null: update to set decision2 = decision
      - If decision2 already set: no-op (logs that decision 2 is not null)
    - Returns 200 with echoed DTO
- Data model:
  - Entity SwipeRecord (table swipe_records)
    - EmbeddedId: SwipeRecordId(profile1Id, profile2Id)
    - Columns: decision1 Boolean, decision2 Boolean

6) Deck (services/deck)
- Role: Intended to host deck-building/scheduling logic; scaffolding present.
- Current state: No endpoints or scheduled tasks yet.
- Configuration:
  - AsyncConfig: @EnableAsync with ThreadPoolTaskExecutor (core 4, max 16, queue 1000, prefix deck-async-)
  - SchedulingConfig: @EnableScheduling (no @Scheduled methods implemented yet)

Cross-cutting components and configuration
- docker-compose.yml (repo root): defines Redis 8.2.1-alpine on 6379 with a basic healthcheck; uses an external network supabase_network_michael.
- Security: Gateway and Profiles reference a Keycloak realm at http://localhost:9080 for JWT validation.
- Service Discovery: Gateway configured to use Eureka at http://localhost:8761/eureka.

Local development — how to run
Prerequisites
- Java 17+ (matching Spring Boot requirements used in modules)
- Maven 3.9+
- Docker (for Redis)
- PostgreSQL reachable at 127.0.0.1:54322 for Swipes service (or adjust services/swipes/src/main/resources/application.yml)
- Optional: Keycloak at http://localhost:9080 for JWT validation (or disable resource server if developing without auth)

Suggested startup order
1) Config Server (port 8888)
   - cd services/config-server2
   - mvn spring-boot:run
2) Discovery (Eureka, default port 8761)
   - cd services/discovery
   - mvn spring-boot:run
3) Infrastructure
   - Redis
     - From repo root: docker compose up -d
   - PostgreSQL for Swipes
     - Option A: local Postgres listening on 54322 (map or configure accordingly)
     - Option B: Docker example:
       docker run --name pg-swipes -e POSTGRES_PASSWORD=postgres -p 54322:5432 -d postgres:16
4) Profiles service (expected at 8010)
   - cd services/profiles
   - Optionally set server.port=8010 (if not already set via external config)
   - mvn spring-boot:run
5) Swipes service (port 8020)
   - cd services/swipes
   - mvn spring-boot:run
6) Gateway (port 8222)
   - cd services/gateway
   - mvn spring-boot:run
7) Deck (optional; no endpoints yet)
   - cd services/deck
   - mvn spring-boot:run

Quick test examples
- Profiles (through Gateway):
  - Create a profile (no auth flow shown here; if JWT is enforced, provide a valid token)
    curl -X POST http://localhost:8222/api/v1/profiles \
      -H 'Content-Type: application/json' \
      -d '{"name": "alice", "age": 26, "bio": "hello"}'
- Swipes (direct to service):
  curl -X POST http://localhost:8020/swipe \
    -H 'Content-Type: application/json' \
    -d '{"profile1Id": "11111111-1111-1111-1111-111111111111", "profile2Id": "22222222-2222-2222-2222-222222222222", "decision": true}'

Notes and gaps
- Deck service: Scheduling is enabled but no scheduled tasks are defined yet. DeckScheduler is currently empty.
- Profiles service: Uses Redis caching and integrates with S3 and geocoding; ensure corresponding credentials/configs are provided (e.g., AWS credentials via environment/instance profile) when using real S3.
- Gateway routing only includes Profiles; you can add routes for Swipes and other services in services/gateway/src/main/resources/application.yml.
- Security (JWT) endpoints will require valid tokens in production; examples above omit Authorization headers for brevity.

Where to find things
- Gateway routes: services/gateway/src/main/resources/application.yml
- Profiles endpoints: services/profiles/src/main/java/com/tinder/profiles/profile/ProfileController.java and photos in .../photos/S3PhotoController.java
- Swipes persistence and API: services/swipes/src/main/java/com/tinder/swipes
- Deck configs: services/deck/src/main/java/com/tinder/deck/config
- Discovery: services/discovery
- Config Server: services/config-server2

Changelog for this document
- Initial documentation added with architecture, services, endpoints, configs, and local run guide.
