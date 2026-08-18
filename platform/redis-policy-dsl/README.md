# Redis policy DSL

This module is the typed desired-state catalog for Redis runtime policy. It currently covers the
shared Redis instance and the Deck keyspaces, retention, client timeouts, and lock-safety rules
that were confirmed by regression tests.

The Kotlin catalog is the policy source of truth for a future control-plane API. Compose and
Spring YAML remain runtime implementations; `RedisPolicyDslTest` compares them with the catalog so
manual drift fails the platform test. `DeckRedisKeys` remains the compile-time source of truth for
the actual shared key format, while the DSL adds ownership, purpose, lifecycle, and operational
risk metadata.

`scripts/validate-redis-config.rb` is an independent Compose deployment guard. It enforces the
catalog policy but is not another policy authoring source.

`controlMode=DESIRED_STATE_READ_ONLY` deliberately exposes desired state without turning a UI
request into a direct production Redis mutation. The catalog also exposes known limitations such
as single-node operation, missing in-project Redis TLS/auth, capacity-alert requirements, and
non-atomic multi-command Deck rewrites.

```kotlin
store("shared-redis") {
    owner = "platform"
    role = StoreRole.REBUILDABLE_CACHE_AND_COORDINATION
    topology = RedisTopology.SINGLE_NODE
    memory {
        maxMemoryBytes = 2 * 1024L * 1024 * 1024
        evictionPolicy = EvictionPolicy.NO_EVICTION
    }
    lock("deck-rebuild") {
        owner = "deck"
        pattern = "deck:lock:{viewerId}"
        leaseMs = 60_000
        maxOperationMs = 30_000
        uniqueOwnerToken = true
        compareAndDeleteRelease = true
    }
}
```

Run `mvn test` to validate the DSL, catalog, and runtime drift. The `main` function prints JSON
suitable for a future read-only API or catalog artifact.
