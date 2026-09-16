# How to describe this work

Do not call it a Tinder clone or a pet project. Describe the failure modes you actually designed for.

## CV / LinkedIn (pick 3–4 lines)

- Designed a matching platform where discovery stays on a CQRS read model (Quarkus) while deck construction and invalidation stay on a Spring write side.
- Made swipe and profile side effects durable with a transactional outbox: batch publish, retry, dead-letter — not fire-and-forget Kafka.
- Split public and internal trust: gateway JWT plus role-aware rate limits; mTLS on profile and swipe-history fanout.
- Java 21 / Spring / Quarkus, Kafka, Redis, PostgreSQL, Keycloak; adjacent services in Go and FastAPI. Shared contracts in a local Maven module. Integration tests use Testcontainers.
- CI enforces Compose policy (Redis, DB roles, mTLS mounts) and selected API/event contracts. Service test suites run locally.

One-line headline:

> Matching platform: CQRS deck reads, transactional outbox, mTLS service boundaries, role-aware gateway limits.

## Cover note / “tell me about a project”

“I built the matching path of a dating product as independently deployable services so a slow deck rebuild cannot sit on the Discover GET, and a broker blip cannot drop a mutual like. The interesting parts are the Deck Read boundary, the outbox publishers, and how the gateway treats user traffic differently from internal profile fanout. I can walk the live path in six minutes or the code in twenty.”

## Phrases to avoid

- “Pet project” / “not finished” / “I used microservices.”
- A list of fifteen service names with no reason.
- “CI is all green so production is proven.” CI is security + policy + contracts.
- Claiming ranking experiments or moderation as production-ready.

If asked about size: “A monorepo with explicit boundaries and `services/tinder-contracts`. Peers are static `*_SERVICE_URL` in Compose. Eureka/Config Server were leftover and are gone.”

## Likely questions

Longer answers with rejected alternatives: [decisions.md](decisions.md).

**Why not one Spring monolith?**
Discover reads and match side effects have different SLO and failure modes. A monolith can still have modules; here the deploy and cache failure domains are actually split. The cost is contracts and outbox, which we then had to take seriously.

**What would you delete?**
Eureka/Config Server (already removed). Ranking admin until `ADMIN` vs `USER_ADMIN` is decided. A live moderation provider until retention and credentials are explicit.

**What broke in production?**
Issuer mismatch: browser tokens carry `https://auth.misyk.tech/realms/spring` while in-cluster discovery advertised `http://keycloak:9080`. Deck Read rejected valid tokens. Gateway also cached a dead Deck Read IP after a `--no-deps` recreate. Both are written up under `docs/features/`.

**How do you test it?**
Per-service Maven/Go/pytest. Testcontainers for Postgres, Redis, Kafka. Quarkus Dev Services on Deck Read. Architecture test that Deck Read cannot absorb Deck’s write responsibilities. I do not pretend GitHub Actions runs those suites today.

**Where is the live demo?**
`https://lunari.misyk.tech` when the origin is up — check [live-stand.md](live-stand.md) the same day. Otherwise the recorded walkthrough and local Compose.

## Recruiter packet (send in this order)

1. This blurb + live URL or recording.
2. Root README (first screen only) and [Issues labeled `story`](https://github.com/Misyk8925/tinder-clone/issues?q=is%3Aissue+label%3Astory).
3. [talk-track.md](talk-track.md) if they ask for a technical screen.
4. One deep link: outbox processor or `DeckReadCqrsBoundaryAcceptanceTest`.
