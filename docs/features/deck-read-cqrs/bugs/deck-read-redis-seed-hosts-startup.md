# Deck Read startup fails on Redis Cluster seed-host configuration

## Status

Fixed locally on 2026-08-20. Deployment and runtime smoke remain outside this repository-only fix.

## Expected versus actual

Both `deck-read-api` and `deck-read-worker` must start with the production Redis Cluster seed hosts configured.

They instead failed before application startup with `SRCFG00039`, because the Redis URI converter received a space after the third comma-separated URI.

## Root cause

The folded YAML scalar for `DECK_READ_REDIS_HOSTS` converted its line break and indentation into a literal space. Quarkus does not accept that space in the Redis URI list.

## Fix and regression evidence

The Compose environment value is now one contiguous line. `DeckReadCqrsBoundaryAcceptanceTest#productionRedisClusterSeedHostsDoNotContainYamlFoldedWhitespace` prevents reintroducing the folded-scalar form.

Rollback is to restore the prior Compose revision; doing so restores the startup failure and is therefore not a normal remediation path.
