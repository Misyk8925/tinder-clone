# Decisions and trade-offs

This is the interview artifact. The spoken version is [talk-track.md](talk-track.md). Issues [#35](https://github.com/Misyk8925/tinder-clone/issues/35)–[#37](https://github.com/Misyk8925/tinder-clone/issues/37) are the same three stories filed as GitHub issues.

Each section is: the failure we were designing for, the cheaper thing we rejected, what shipped, and what you pay for it.

---

## Why a transactional outbox (not “save, then send”)

**Failure.** A swipe or profile write can commit in Postgres and die before `KafkaTemplate.send()` returns. The match then exists only in one service’s memory, or never exists. Support cannot replay what was never recorded.

**Rejected.** Dual-write in the request thread (DB + Kafka). It looks like one sequence diagram. It is two commits with no joint abort. Also rejected: “the consumer will notice eventually” without an outbox row — there is nothing to retry.

**Shipped.** The business transaction inserts the aggregate and an outbox row together. A batch publisher claims unpublished rows, publishes, retries with backoff, and dead-letters after max attempts instead of deleting the evidence. Profiles: `ProfileOutboxService` + `ProfileOutboxBatchProcessor`. Swipes/matches: `SwipeOutboxEventDispatcher`, `MatchOutboxBatchProcessor`.

**Cost.** At-least-once delivery. Consumers must be idempotent. Lag between commit and Kafka is visible. We accepted that over a lost mutual like.

**Why it shows up in seeding.** A SQL insert into `profiles` never writes `profile.created`, so Deck Read stays `202 BUILDING`. That is the outbox invariant, not a docs quirk. See [seed-notes.md](seed-notes.md).

---

## Why CQRS for the discovery deck (not a live join on GET)

**Failure.** Discover is the hottest read. Building order from Profiles + location + swipe history on every `GET /api/v2/deck` couples user latency to write-side work. A profile change must not rebuild every deck. A cold cache must not look like “nobody nearby.”

**Rejected.** One Spring service that both ranks and serves. Also rejected: join Profiles and swipe history on the request path and hide misses as an empty list.

**Shipped.** `services/deck` owns build, reverse-index invalidation, and `POST /api/v1/internal/deck/ensure`. `services/deck-read` (Quarkus) owns the client-facing read model: `GET /api/v1/deck` and `GET /api/v2/deck` (the Angular client uses **v2**). On a miss it calls ensure, then hydrates cards. The browser never calls Deck. Proof that the boundary does not drift: `DeckReadCqrsBoundaryAcceptanceTest`.

**Cost.** An extra hop and a second Redis (Deck Read’s own instance) instead of one “smart” service. We accepted that so a Profiles outage or a slow rebuild does not sit on Discover GET. Honest empty vs building is `202` / client “preparing”, not a fake empty list.

---

## Why mTLS on internal fanout (not the user JWT on every port)

**Failure.** One JWT on every port treats “the user asked for their deck” the same as “Deck asked for someone else’s profile and swipe history.” A shared public rate limit lets a swipe storm starve Discover, or the reverse.

**Rejected.** Network-only trust (“it is on the Docker network”). Also rejected: passing the browser token through to Profiles `:8011` / Consumer `:8051`.

**Shipped.** Browser → gateway only (Keycloak realm `spring`). `RoleBasedRateLimitFilter` keys on `routeId + user + role`; capacity `0` is deny. Internal listeners use mTLS. Compose mounts are checked in CI (`scripts/validate-compose-mtls-mounts.rb`).

**Cost.** Certificate and issuer operations. Public `iss` vs in-cluster Keycloak URL has already broken Deck Read once. We still keep user tokens off internal profile-id fanout.

---

## Why Quarkus only on Deck Read (not a platform rewrite)

**Failure.** Discover has a tighter latency SLO than profile writes or swipe persistence. The write side already had Spring, JPA, outbox, and Testcontainers.

**Rejected.** Rewrite Profiles/Deck/Gateway to Quarkus for consistency. Also rejected: keep Discover on the Spring write service and “make Redis faster.”

**Shipped.** Deck Read is a Quarkus service so the read hop can stay small (reactive HTTP, separate Redis, own Kafka groups). Deck, Profiles, Consumer, Gateway stay Spring. Shared types live in `services/tinder-contracts`.

**Cost.** Two JVM stacks in one repo (Quarkus config vs Spring). The team must not let Deck Read absorb Deck’s ranking or Redis keys — that is what the architecture test is for. We did not put Quarkus on swipe-write; Compose production swipe-write is Go (`swipes-go`) for a different reason (write throughput), with `swipes-demo` as the Java rollback overlay.

---

## Nearby decisions (one line each)

| Choice | Rejected | Why |
|---|---|---|
| Static `*_SERVICE_URL` in production | Eureka / Config Server | Those are leftover local wiring; they are off in Compose prod. |
| Gateway as the only browser entry | Client talking to many origins | JWT, CORS, and rate limits stay in one place. |
| Photos as its own FastAPI service | Profiles writing S3 bytes | Upload policy, variants, and object storage fail independently of profile JSON. |
| Moderation as its own service (Phase 4 in repo) | Inline checks in Profiles/Match | Classifier, policy versions, and review tasks are a different consistency domain. Not on the six-minute demo path; Phase 5 (live providers / release) is unfinished. |
| No SQL seeds for decks | Fixture rows in Postgres | Missing outbox events leave the read model unbuilt. |

---

## What I would not defend in an interview

- Ranking admin / experiments as the live default (`ADMIN` vs `USER_ADMIN` is still an owner decision).
- Popularity ranker on production Discover.
- Moderation Phase 5 (OpenAI/Gemini as if they were wired and retention-approved).
- “CI is green, therefore the product runs.” CI is policy + security, not `mvn test`.
