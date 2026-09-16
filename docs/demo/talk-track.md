# Interview talk track

Three stories. Each is four to six minutes. Start from a product failure, then point at one class and one test. The public copies are GitHub issues labeled [`story`](https://github.com/Misyk8925/tinder-clone/issues?q=is%3Aissue+label%3Astory); this file is spoken prep, not a second catalog.

The written interview sheet (rejected alternatives, not just the spoken path) is [decisions.md](decisions.md).

---

## 1. CQRS for the discovery deck

**Problem.** Discovery is the hottest read. Building a ranked list from Profiles + location + swipe history on every `GET` couples latency to write-side work. A profile change must not require rebuilding every deck, and a cold cache must not look like “you have no one nearby.”

**What we did.** `services/deck` is the write side: it builds order, keeps reverse indexes, exposes `POST /api/v1/internal/deck/ensure`. `services/deck-read` (Quarkus) is the client-facing read model: `GET /api/v1/deck` and `GET /api/v2/deck` (the Angular client uses **v2**). On a miss it asks Deck to ensure, then hydrates card payloads. The browser never calls Deck.

**Where to open**

- Boundary and artifacts: [`docs/features/deck-read-cqrs/README.md`](../features/deck-read-cqrs/README.md)
- The line that must not drift: Deck Read may read Deck’s ordering and call ensure; it must not own Deck’s algorithm or Redis keys.
- Proof: [`services/deck-read/src/test/java/com/tinder/deckread/architecture/DeckReadCqrsBoundaryAcceptanceTest.java`](../../services/deck-read/src/test/java/com/tinder/deckread/architecture/DeckReadCqrsBoundaryAcceptanceTest.java)
- Client: [`clients/tinder-client/src/app/core/services/profile.service.ts`](../../clients/tinder-client/src/app/core/services/profile.service.ts) (`/api/v2/deck`)
- Gateway routes both versions to Deck Read (`application.yml` path `/api/v1/deck,/api/v2/deck`)

**Trade-off.** An extra hop and a second Redis (Deck Read has its own cluster) instead of one “smart” service. We accepted that so a Profiles outage or a slow rebuild does not sit on the Discover GET. The honest empty/building signal is `202` / client “preparing”, not a fake empty list.

**If they ask about ranking.** Baseline is what the demo serves. Popularity / experiments are implemented but have not been the live default; admin role naming (`ADMIN` vs `USER_ADMIN`) is still an owner decision. Do not demo admin.

---

## 2. Transactional outbox

**Problem.** “Swipe saved, therefore Kafka got it” is a lie. A process can commit Postgres and die before `send()`. A match that exists only in memory is a support incident.

**What we did.** Profile mutations enqueue `PROFILE_CREATED` / updated / deleted in the same transaction (`ProfileOutboxService`). A batch job locks a page of unpublished rows and publishes them (`ProfileOutboxBatchProcessor`): success → published; retry with backoff; after max attempts → dead-letterled, not deleted. The consumer does the same for swipe-saved and match-created (`SwipeOutboxEventDispatcher`, `MatchOutboxBatchProcessor`).

**Where to open**

- [`services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxBatchProcessor.java`](../../services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxBatchProcessor.java)
- [`services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxEventDispatcher.java`](../../services/profiles/src/main/java/com/tinder/profiles/infrastructure/messaging/outbox/ProfileOutboxEventDispatcher.java)
- [`services/consumer/src/main/java/com/tinder/clone/consumer/outbox/SwipeOutboxEventDispatcher.java`](../../services/consumer/src/main/java/com/tinder/clone/consumer/outbox/SwipeOutboxEventDispatcher.java)
- Tests: `ProfileOutboxBatchProcessorTest`, `WriteUseCaseOutboxTest`, consumer `OutboxPublishErrorsTest`

**Trade-off.** At-least-once delivery. Consumers must be idempotent. We chose that over a dual-write that looks simpler in a sequence diagram and loses the match on the first broker blip. Visibility of failed / dead-lettered rows is part of the design, not an afterthought.

**Seed implication.** A row stuffed into `profiles` with SQL never writes an outbox row, so Deck Read never sees `profile.created`. That is why [seed-notes.md](seed-notes.md) forbids SQL seeds.

---

## 3. Security boundaries

**Problem.** One JWT on every port treats “the user asked for their deck” the same as “Deck asked for someone else’s profile and swipe history.” A shared public limit lets a swipe storm starve Discover, or the reverse.

**What we did.**

- Browser → gateway only. JWT from Keycloak realm `spring`.
- `RoleBasedRateLimitFilter`: key is `routeId + user + role`. Discover GETs cannot empty the swipe bucket. Capacity `0` is a deny, not “use the default.” Roles: `anon` / `basic` / `premium` / `admin`.
- Internal listeners (Profiles `:8011`, Consumer `:8051`, Deck ensure) use mTLS. Compose mounts are checked by [`scripts/validate-compose-mtls-mounts.rb`](../../scripts/validate-compose-mtls-mounts.rb) in the Policy workflow.

**Where to open**

- [`services/gateway/src/main/java/com/tinder/gateway/RoleBasedRateLimitFilter.java`](../../services/gateway/src/main/java/com/tinder/gateway/RoleBasedRateLimitFilter.java)
- [`services/gateway/src/test/java/com/tinder/gateway/RoleBasedRateLimitFilterTest.java`](../../services/gateway/src/test/java/com/tinder/gateway/RoleBasedRateLimitFilterTest.java)
- Profiles internal server config under `services/profiles/src/main/java/com/tinder/profiles/config/mtls/`
- CI: [`.github/workflows/policy.yml`](../../.github/workflows/policy.yml) (redis policy, DB roles, mTLS mounts, Deck Read contracts)
- Security workflow is CodeQL / Trivy / dependency-review, **not** `mvn test`

**Trade-off.** Certificate and issuer operational cost (public `iss` vs in-cluster Keycloak URL has already bitten Deck Read). We still keep user tokens off internal profile-id fanout.

---

## Short answers

**Why so many services?** Each one owns a failure mode: deck build, deck read, swipe write, match projection, chat, photos, billing. Shared types live in `services/tinder-contracts`. You do not need to walk every folder — [learn.md](learn.md) is the twelve-file map.

**Where is Eureka?** It isn't. Peers are static `*_SERVICE_URL` in Compose. The old Config Server and discovery modules were leftover local wiring and were removed so nobody has to explain a disabled server.

**Why Quarkus only on Deck Read?** That hop is the Discover SLO. The write side stayed Spring. Full write-up: [decisions.md](decisions.md).

**Why Go swipes?** Compose production path is `services/swipes-go`. `services/swipes-demo` is the Java original and the rollback overlay, not what you start for a demo.

**Is CI green equal to “it runs”?** No. CI watches security, Compose policy, and selected contracts. Service tests are Maven / Go / pytest / Testcontainers on the developer machine.

**Why not show moderation / ranker?** Both are in-progress or unproven on the live contour. Baseline matching is the story you can defend.
