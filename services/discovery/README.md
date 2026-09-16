# discovery

Local-only Eureka leftover. Off in production Compose.

Parent map: [Lunari README](../../README.md) · Domain: legacy

## Boundary

**Owns.** Optional local service discovery if someone still starts it by hand.

**Does not own.** Production service resolution. Compose prod comments this out; services use static `*_SERVICE_URL`.

**Neighbors.** [config-server2](../config-server2/README.md). Not on `./scripts/demo-up.sh`.

## Contents

Spring Cloud Eureka server. Commented out of `docker-compose.yml`.

## Decision

**Failure.** Client-side discovery was leftover local wiring, not how Dokploy/Compose run the stand.

**Rejected.** Eureka in production.

**Shipped.** Static URLs. Keep the module so a hybrid debug session can still opt in.

**Cost.** Do not treat a running Eureka as a product requirement.

## Validate

Not part of the demo.
