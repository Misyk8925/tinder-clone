# config-server2

Local-only Spring Cloud Config leftover. Off in production Compose.

Parent map: [Lunari README](../../README.md) · Domain: legacy

## Boundary

**Owns.** Optional local config-server wiring if someone still starts it by hand.

**Does not own.** Production configuration. Services resolve peers with static `*_SERVICE_URL`. `EUREKA_CLIENT_ENABLED=false` in prod-shaped Compose.

**Neighbors.** None on the demo or production path. Pair leftover: [discovery](../discovery/README.md).

## Contents

Standard Spring Cloud Config app. Commented out of `docker-compose.yml`.

## Decision

**Failure.** Eureka + Config Server added moving parts to a fleet that is deployed with known URLs.

**Rejected.** Keep them as the production discovery story.

**Shipped.** Static URLs. This directory remains so hybrid/local experiments do not have to reconstruct it.

**Cost.** Dead code in the tree. Do not present it as architecture.

## Validate

Not part of the demo. Do not start it unless you are debugging the leftover path.
