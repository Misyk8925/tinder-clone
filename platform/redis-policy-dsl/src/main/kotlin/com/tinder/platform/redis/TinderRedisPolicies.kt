package com.tinder.platform.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

private const val GIB = 1024L * 1024 * 1024

object TinderRedisPolicies {
    val catalog: RedisPolicyCatalog = redisPolicies {
        store("shared-redis") {
            owner = "platform"
            role = StoreRole.REBUILDABLE_CACHE_AND_COORDINATION
            topology = RedisTopology.SINGLE_NODE

            persistence {
                mode = PersistenceMode.AOF
                appendFsync = AppendFsync.EVERY_SECOND
                dataVolume = "redis-data:/data"
            }
            memory {
                maxMemoryBytes = 2 * GIB
                evictionPolicy = EvictionPolicy.NO_EVICTION
            }
            security {
                tls = false
                authentication = false
            }
            client("deck") {
                commandTimeoutMs = 2_000
                connectTimeoutMs = 2_000
            }
            client("deck-read") {
                commandTimeoutMs = 2_000
                connectTimeoutMs = 2_000
            }

            namespace("deck-materialized") {
                owner = "deck"
                pattern = "deck:{viewerId}"
                kind = NamespaceKind.MATERIALIZED_VIEW
                ttlSeconds = 60 * 60
            }
            namespace("deck-build-metadata") {
                owner = "deck"
                pattern = "deck:build:ts:{viewerId}"
                kind = NamespaceKind.BUILD_METADATA
                ttlSeconds = 60 * 60
            }
            namespace("deck-reverse-index") {
                owner = "deck"
                pattern = "deck:contains:{profileId}"
                kind = NamespaceKind.REVERSE_INDEX
                ttlSeconds = 60 * 60
            }
            namespace("deck-recent-viewers") {
                owner = "deck"
                pattern = "deck:recent:viewers"
                kind = NamespaceKind.ACTIVITY_INDEX
                pruneWindowSeconds = 30 * 60
                readLimit = 1_000
            }
            namespace("deck-stale-markers") {
                owner = "deck"
                pattern = "deck:stale:{viewerId}"
                kind = NamespaceKind.STALE_MARKER
                ttlSeconds = 24 * 60 * 60
            }
            namespace("deck-profile-invalidated-markers") {
                owner = "deck"
                pattern = "deck:profile:invalidated-at:{profileId}"
                kind = NamespaceKind.PROFILE_MARKER
                ttlSeconds = 24 * 60 * 60
            }
            namespace("deck-profile-deleted-markers") {
                owner = "deck"
                pattern = "deck:profile:deleted"
                kind = NamespaceKind.PROFILE_MARKER
                ttlSeconds = 24 * 60 * 60
            }
            namespace("deck-preferences-cache") {
                owner = "deck"
                pattern = "prefs:{minAge}:{maxAge}:{gender}"
                kind = NamespaceKind.PREFERENCES_CACHE
                ttlSeconds = 5 * 60
            }
            lock("deck-rebuild") {
                owner = "deck"
                pattern = "deck:lock:{viewerId}"
                leaseMs = 60_000
                maxOperationMs = 30_000
                uniqueOwnerToken = true
                compareAndDeleteRelease = true
            }

            risk(OperationalRisk.SINGLE_NODE_NO_FAILOVER)
            risk(OperationalRisk.NO_TRANSPORT_OR_CLIENT_AUTH)
            risk(OperationalRisk.MULTI_COMMAND_WRITES_NOT_CRASH_ATOMIC)
            risk(OperationalRisk.NO_EVICTION_REQUIRES_CAPACITY_ALERTS)

            runtimeSource("docker-compose.yml")
            runtimeSource("services/deck/src/main/resources/application.yml")
            runtimeSource("services/deck/src/main/java/com/tinder/deck/service/DeckCache.java")
            runtimeSource("services/tinder-contracts/src/main/java/com/tinder/contracts/deck/DeckRedisKeys.java")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
        }

        store("deck-read-cluster") {
            owner = "deck-read"
            role = StoreRole.REBUILDABLE_CACHE_AND_COORDINATION
            topology = RedisTopology.CLUSTER

            persistence {
                mode = PersistenceMode.AOF
                appendFsync = AppendFsync.EVERY_SECOND
                dataVolume = "deck-read-redis-1-data:/data"
            }
            memory {
                maxMemoryBytes = 2 * GIB
                evictionPolicy = EvictionPolicy.NO_EVICTION
            }
            security {
                tls = false
                authentication = false
            }
            client("deck-read") {
                commandTimeoutMs = 2_000
                connectTimeoutMs = 2_000
            }

            namespace("dr-viewer") {
                owner = "deck-read"
                pattern = "dr:viewer:{viewerProfileId}"
                kind = NamespaceKind.MATERIALIZED_VIEW
            }
            namespace("dr-profile-card") {
                owner = "deck-read"
                pattern = "dr:profile:{profileId}:card"
                kind = NamespaceKind.MATERIALIZED_VIEW
            }
            namespace("dr-user-profile") {
                owner = "deck-read"
                pattern = "dr:user:{viewerUserId}:profile"
                kind = NamespaceKind.MATERIALIZED_VIEW
            }
            namespace("dr-hot-viewers") {
                owner = "deck-read"
                pattern = "dr:profile:{profileId}:hot-viewers"
                kind = NamespaceKind.ACTIVITY_INDEX
            }
            namespace("dr-readiness") {
                owner = "deck-read"
                pattern = "dr:read-model:ready"
                kind = NamespaceKind.BUILD_METADATA
            }
            lock("dr-build") {
                owner = "deck-read"
                pattern = "dr:viewer:{viewerProfileId}:build-lock"
                leaseMs = 60_000
                maxOperationMs = 30_000
                uniqueOwnerToken = true
                compareAndDeleteRelease = true
            }
            lock("dr-reconciliation") {
                owner = "deck-read"
                pattern = "dr:reconciliation:lease"
                leaseMs = 60_000
                maxOperationMs = 55_000
                uniqueOwnerToken = true
                compareAndDeleteRelease = true
            }

            risk(OperationalRisk.NO_TRANSPORT_OR_CLIENT_AUTH)
            risk(OperationalRisk.MULTI_COMMAND_WRITES_NOT_CRASH_ATOMIC)
            risk(OperationalRisk.NO_EVICTION_REQUIRES_CAPACITY_ALERTS)

            runtimeSource("docker-compose.yml")
            runtimeSource("services/deck-read/src/main/resources/application.properties")
            runtimeSource("services/deck-read/src/main/java/com/tinder/deckread/readmodel/ReadModelKeys.java")
        }
    }

    fun json(): String = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .writeValueAsString(catalog)
}

fun main(args: Array<String>) {
    val json = TinderRedisPolicies.json()
    if (args.isNotEmpty()) {
        val file = java.io.File(args[0])
        file.parentFile?.mkdirs()
        file.writeText(json)
    } else {
        println(json)
    }
}
