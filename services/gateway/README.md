# Gateway

Single browser entry point. Authenticates Keycloak JWTs and applies per-route, per-role rate limits.

Parent map: [Lunari README](../../README.md) · Domain: edge

## Boundary

**Owns.** Public HTTP/WS routing to domain services. JWT validation (realm `spring`). `RoleBasedRateLimitFilter` keyed by `routeId + user + role`. Capacity `0` is deny, not “use the default.” `PremiumOrAdminFilter` on super-like and liked-me. Stripe webhook is forwarded without the JWT limiter.

**Does not own.** Domain logic, ranking, match creation, photo bytes, geocoding. Mutual TLS between internal listeners — gateway only proxies public paths. Service discovery (production uses static `*_SERVICE_URL`).

**Neighbors.** [tinder-client](../../clients/tinder-client/README.md) and [tinder-admin](../../clients/tinder-admin/README.md) are the only browsers that should talk to it. Downstream: [profiles](../profiles/README.md), [deck-read](../deck-read/README.md), [swipes-go](../swipes-go/README.md), [consumer](../consumer/README.md), [match](../match/README.md), [subscriptions](../subscriptions/README.md), [deck](../deck/README.md) (admin only).

## Contents

- `src/main/resources/application.yml` — route table (`profile-*`, `deck-read`, `swipes`, `consumer-liked-me`, `deck-*-admin`, `match-*`, `subscriptions-*`).
- `RoleBasedRateLimitFilter` — roles `anon` / `basic` / `premium` / `admin`.
- Port `:8222`. Health: `GET /actuator/health`.

## Decision

**Failure.** One shared public bucket lets a swipe storm starve Discover, or the reverse. One JWT on every port treats “the user asked for their deck” the same as “Deck asked for someone else’s profile.”

**Rejected.** Client talking to many origins. Network-only trust. Passing the browser token through to Profiles `:8011` / Consumer `:8051`.

**Shipped.** Browser → gateway only. Route-scoped limits. Internal fanout stays on mTLS, off this process.

**Cost.** Certificate and issuer operations live on the services behind the gateway, not here. After a `--no-deps` recreate of `deck-read-api`, restart gateway or Discover returns 500 (stale Netty DNS).

## Validate

```bash
mvn test
```

See [docs/demo/decisions.md](../../docs/demo/decisions.md) (mTLS / gateway story).
