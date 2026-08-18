package com.tinder.platform.redis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RedisPolicyDslTest {

    @Test
    fun `coordination store cannot silently evict keys`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            minimalStore {
                memory {
                    maxMemoryBytes = 1024
                    evictionPolicy = EvictionPolicy.ALLKEYS_LRU
                }
            }
        }

        assertTrue(error.message!!.contains("noeviction"))
    }

    @Test
    fun `lock lease must exceed its bounded operation time`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            minimalStore {
                lock("unsafe") {
                    owner = "test"
                    pattern = "test:lock:{id}"
                    leaseMs = 30_000
                    maxOperationMs = 30_000
                    uniqueOwnerToken = true
                    compareAndDeleteRelease = true
                }
            }
        }

        assertTrue(error.message!!.contains("lease must exceed"))
    }

    @Test
    fun `project catalog represents the fixed shared Redis and Deck policies`() {
        val store = TinderRedisPolicies.catalog.stores.single { it.name == "shared-redis" }

        assertEquals("shared-redis", store.name)
        assertEquals(PersistenceMode.AOF, store.persistence.mode)
        assertEquals(AppendFsync.EVERY_SECOND, store.persistence.appendFsync)
        assertEquals(EvictionPolicy.NO_EVICTION, store.memory.evictionPolicy)
        assertEquals(2_147_483_648L, store.memory.maxMemoryBytes)
        assertEquals(2_000, store.clients.single { it.service == "deck" }.commandTimeoutMs)
        assertEquals(60_000, store.locks.single { it.name == "deck-rebuild" }.leaseMs)
        assertTrue(OperationalRisk.SINGLE_NODE_NO_FAILOVER in store.operationalRisks)
        assertTrue(OperationalRisk.MULTI_COMMAND_WRITES_NOT_CRASH_ATOMIC in store.operationalRisks)
    }

    @Test
    fun `project catalog represents every managed Deck namespace`() {
        assertEquals(
            setOf(
                "deck:{viewerId}",
                "deck:build:ts:{viewerId}",
                "deck:contains:{profileId}",
                "deck:recent:viewers",
                "deck:stale:{viewerId}",
                "deck:profile:invalidated-at:{profileId}",
                "deck:profile:deleted",
                "prefs:{minAge}:{maxAge}:{gender}"
            ),
            TinderRedisPolicies.catalog.stores.single { it.name == "shared-redis" }.namespaces.map { it.pattern }.toSet()
        )
    }

    @Test
    fun `catalog is exportable for a future read-only control plane API`() {
        val json = TinderRedisPolicies.json()

        assertTrue(json.contains("\"controlMode\" : \"DESIRED_STATE_READ_ONLY\""))
        assertTrue(json.contains("\"name\" : \"shared-redis\""))
        assertTrue(json.contains("\"name\" : \"deck-read-cluster\""))
        assertTrue(json.contains("\"operationalRisks\""))
        assertTrue(json.contains("\"runtimeSources\""))
    }

    @Test
    fun `runtime Redis configuration matches the desired catalog`() {
        val repositoryRoot = Path.of(System.getProperty("user.dir")).resolve("../..").normalize()
        val compose = Files.readString(repositoryRoot.resolve("docker-compose.yml"))
        val application = Files.readString(
            repositoryRoot.resolve("services/deck/src/main/resources/application.yml")
        )
        val keySchema = Files.readString(
            repositoryRoot.resolve(
                "services/tinder-contracts/src/main/java/com/tinder/contracts/deck/DeckRedisKeys.java"
            )
        )
        val store = TinderRedisPolicies.catalog.stores.single { it.name == "shared-redis" }
        val deckClient = store.clients.single { it.service == "deck" }
        val lock = store.locks.single { it.name == "deck-rebuild" }
        val namespaces = store.namespaces.associateBy { it.name }
        val redisService = composeService(compose, "redis")
        val deckService = composeService(compose, "deck")

        assertTrue(redisService.contains("--appendonly yes --appendfsync everysec"))
        assertTrue(redisService.contains("--maxmemory 2gb --maxmemory-policy noeviction"))
        assertTrue(redisService.contains("- ${store.persistence.dataVolume}"))
        assertTrue(deckService.contains("SPRING_DATA_REDIS_TIMEOUT: \${DECK_REDIS_TIMEOUT:-${deckClient.commandTimeoutMs / 1000}s}"))
        assertTrue(deckService.contains("SPRING_DATA_REDIS_CONNECT_TIMEOUT: \${DECK_REDIS_CONNECT_TIMEOUT:-${deckClient.connectTimeoutMs / 1000}s}"))
        assertTrue(application.contains("lock-timeout-seconds: ${lock.leaseMs / 1000}"))
        assertTrue(application.contains("user-rebuild-timeout-seconds: ${lock.maxOperationMs / 1000}"))
        assertTrue(application.contains("  ttl-minutes: ${namespaces.getValue("deck-materialized").ttlSeconds!! / 60}"))
        assertTrue(application.contains("preferences-cache-ttl-minutes: ${namespaces.getValue("deck-preferences-cache").ttlSeconds!! / 60}"))
        assertTrue(application.contains("ttl-hours: ${namespaces.getValue("deck-stale-markers").ttlSeconds!! / 3600}"))
        assertTrue(application.contains("recent-viewers-window-minutes: ${namespaces.getValue("deck-recent-viewers").pruneWindowSeconds!! / 60}"))
        assertTrue(application.contains("max-recent-viewers: ${namespaces.getValue("deck-recent-viewers").readLimit}"))

        assertTrue(keySchema.contains("PRIMARY_DECK_PREFIX = \"deck:\""))
        assertTrue(keySchema.contains("\"deck:build:ts:\""))
        assertTrue(keySchema.contains("\"deck:contains:\""))
        assertTrue(keySchema.contains("RECENT_VIEWERS = \"deck:recent:viewers\""))
        assertTrue(keySchema.contains("\"deck:stale:\""))
        assertTrue(keySchema.contains("\"deck:profile:invalidated-at:\""))
        assertTrue(keySchema.contains("DELETED_PROFILES = \"deck:profile:deleted\""))
        assertTrue(keySchema.contains("\"prefs:%d:%d:%s\""))

        store.runtimeSources.forEach { source ->
            assertTrue(Files.exists(repositoryRoot.resolve(source)), "Missing runtime source: $source")
        }
    }

    @Test
    fun `project catalog represents the Deck-Read cluster store`() {
        val store = TinderRedisPolicies.catalog.stores.single { it.name == "deck-read-cluster" }

        assertEquals(RedisTopology.CLUSTER, store.topology)
        assertEquals(EvictionPolicy.NO_EVICTION, store.memory.evictionPolicy)
        assertEquals("deck-read", store.clients.single().service)
        assertTrue(store.namespaces.map { it.pattern }.contains("dr:viewer:{viewerProfileId}"))
        assertEquals("dr:reconciliation:lease", store.locks.single { it.name == "dr-reconciliation" }.pattern)
    }

    @Test
    fun `runtime Deck-Read cluster configuration matches the desired catalog`() {
        val repositoryRoot = Path.of(System.getProperty("user.dir")).resolve("../..").normalize()
        val compose = Files.readString(repositoryRoot.resolve("docker-compose.yml"))
        val keys = Files.readString(
            repositoryRoot.resolve("services/deck-read/src/main/java/com/tinder/deckread/readmodel/ReadModelKeys.java")
        )
        val store = TinderRedisPolicies.catalog.stores.single { it.name == "deck-read-cluster" }
        val node = composeService(compose, "deck-read-redis-1")

        assertTrue(node.contains("--cluster-enabled yes"))
        assertTrue(node.contains("--appendonly yes --appendfsync everysec"))
        assertTrue(node.contains("--maxmemory-policy noeviction"))
        assertTrue(compose.contains(store.persistence.dataVolume))
        assertTrue(keys.contains("\"dr:viewer:{\""))
        assertTrue(keys.contains("\"dr:profile:{\""))
        assertTrue(keys.contains("\"dr:read-model:ready\""))

        store.runtimeSources.forEach { source ->
            assertTrue(Files.exists(repositoryRoot.resolve(source)), "Missing runtime source: $source")
        }
    }

    private fun composeService(compose: String, service: String): String {
        val marker = "\n  $service:\n"
        val start = compose.indexOf(marker)
        require(start >= 0) { "Compose service not found: $service" }
        val contentStart = start + marker.length
        val nextService = Regex("\\n  [a-zA-Z0-9_-]+:\\n").find(compose, contentStart)
        return compose.substring(contentStart, nextService?.range?.first ?: compose.length)
    }

    private fun minimalStore(block: RedisStorePolicyBuilder.() -> Unit): RedisPolicyCatalog = redisPolicies {
        store("test") {
            owner = "test"
            role = StoreRole.REBUILDABLE_CACHE_AND_COORDINATION
            topology = RedisTopology.SINGLE_NODE
            persistence {
                mode = PersistenceMode.AOF
                appendFsync = AppendFsync.EVERY_SECOND
                dataVolume = "test:/data"
            }
            memory {
                maxMemoryBytes = 1024
                evictionPolicy = EvictionPolicy.NO_EVICTION
            }
            security {
                tls = false
                authentication = false
            }
            risk(OperationalRisk.SINGLE_NODE_NO_FAILOVER)
            risk(OperationalRisk.NO_TRANSPORT_OR_CLIENT_AUTH)
            risk(OperationalRisk.NO_EVICTION_REQUIRES_CAPACITY_ALERTS)
            block()
        }
    }
}
