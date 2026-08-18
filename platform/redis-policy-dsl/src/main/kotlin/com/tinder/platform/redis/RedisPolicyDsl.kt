package com.tinder.platform.redis

@DslMarker
annotation class RedisPolicyDsl

enum class StoreRole {
    REBUILDABLE_CACHE_AND_COORDINATION
}

enum class RedisTopology {
    SINGLE_NODE,
    CLUSTER
}

enum class PersistenceMode {
    NONE,
    AOF
}

enum class AppendFsync {
    ALWAYS,
    EVERY_SECOND,
    NEVER
}

enum class EvictionPolicy {
    NO_EVICTION,
    ALLKEYS_LRU
}

enum class NamespaceKind {
    MATERIALIZED_VIEW,
    BUILD_METADATA,
    REVERSE_INDEX,
    ACTIVITY_INDEX,
    STALE_MARKER,
    PROFILE_MARKER,
    PREFERENCES_CACHE
}

enum class OperationalRisk {
    SINGLE_NODE_NO_FAILOVER,
    NO_TRANSPORT_OR_CLIENT_AUTH,
    MULTI_COMMAND_WRITES_NOT_CRASH_ATOMIC,
    NO_EVICTION_REQUIRES_CAPACITY_ALERTS
}

data class RedisPersistencePolicy(
    val mode: PersistenceMode,
    val appendFsync: AppendFsync,
    val dataVolume: String
)

data class RedisMemoryPolicy(
    val maxMemoryBytes: Long,
    val evictionPolicy: EvictionPolicy
)

data class RedisSecurityPolicy(
    val tls: Boolean,
    val authentication: Boolean
)

data class RedisClientPolicy(
    val service: String,
    val commandTimeoutMs: Long,
    val connectTimeoutMs: Long
)

data class RedisNamespacePolicy(
    val name: String,
    val owner: String,
    val pattern: String,
    val kind: NamespaceKind,
    val ttlSeconds: Long?,
    val pruneWindowSeconds: Long?,
    val readLimit: Int?
)

data class RedisLockPolicy(
    val name: String,
    val owner: String,
    val pattern: String,
    val leaseMs: Long,
    val maxOperationMs: Long,
    val uniqueOwnerToken: Boolean,
    val compareAndDeleteRelease: Boolean
)

data class RedisStorePolicy(
    val name: String,
    val owner: String,
    val role: StoreRole,
    val topology: RedisTopology,
    val persistence: RedisPersistencePolicy,
    val memory: RedisMemoryPolicy,
    val security: RedisSecurityPolicy,
    val clients: List<RedisClientPolicy>,
    val namespaces: List<RedisNamespacePolicy>,
    val locks: List<RedisLockPolicy>,
    val operationalRisks: Set<OperationalRisk>,
    val runtimeSources: List<String>
)

data class RedisPolicyCatalog(
    val schemaVersion: Int,
    val controlMode: String,
    val stores: List<RedisStorePolicy>
)

@RedisPolicyDsl
class RedisPoliciesBuilder {
    private val stores = mutableListOf<RedisStorePolicy>()

    fun store(name: String, block: RedisStorePolicyBuilder.() -> Unit) {
        stores += RedisStorePolicyBuilder(name).apply(block).build()
    }

    internal fun build(): RedisPolicyCatalog {
        val catalog = RedisPolicyCatalog(
            schemaVersion = 1,
            controlMode = "DESIRED_STATE_READ_ONLY",
            stores = stores.toList()
        )
        RedisPolicyValidator.validate(catalog)
        return catalog
    }
}

@RedisPolicyDsl
class RedisStorePolicyBuilder internal constructor(private val name: String) {
    lateinit var owner: String
    lateinit var role: StoreRole
    lateinit var topology: RedisTopology
    private var persistence: RedisPersistencePolicy? = null
    private var memory: RedisMemoryPolicy? = null
    private var security: RedisSecurityPolicy? = null
    private val clients = mutableListOf<RedisClientPolicy>()
    private val namespaces = mutableListOf<RedisNamespacePolicy>()
    private val locks = mutableListOf<RedisLockPolicy>()
    private val operationalRisks = mutableSetOf<OperationalRisk>()
    private val runtimeSources = mutableListOf<String>()

    fun persistence(block: RedisPersistencePolicyBuilder.() -> Unit) {
        persistence = RedisPersistencePolicyBuilder().apply(block).build()
    }

    fun memory(block: RedisMemoryPolicyBuilder.() -> Unit) {
        memory = RedisMemoryPolicyBuilder().apply(block).build()
    }

    fun security(block: RedisSecurityPolicyBuilder.() -> Unit) {
        security = RedisSecurityPolicyBuilder().apply(block).build()
    }

    fun client(service: String, block: RedisClientPolicyBuilder.() -> Unit) {
        clients += RedisClientPolicyBuilder(service).apply(block).build()
    }

    fun namespace(name: String, block: RedisNamespacePolicyBuilder.() -> Unit) {
        namespaces += RedisNamespacePolicyBuilder(name).apply(block).build()
    }

    fun lock(name: String, block: RedisLockPolicyBuilder.() -> Unit) {
        locks += RedisLockPolicyBuilder(name).apply(block).build()
    }

    fun risk(risk: OperationalRisk) {
        operationalRisks += risk
    }

    fun runtimeSource(path: String) {
        runtimeSources += path
    }

    internal fun build() = RedisStorePolicy(
        name = name,
        owner = owner,
        role = role,
        topology = topology,
        persistence = requireNotNull(persistence) { "$name: persistence policy must be declared" },
        memory = requireNotNull(memory) { "$name: memory policy must be declared" },
        security = requireNotNull(security) { "$name: security policy must be declared" },
        clients = clients.toList(),
        namespaces = namespaces.toList(),
        locks = locks.toList(),
        operationalRisks = operationalRisks.toSet(),
        runtimeSources = runtimeSources.toList()
    )
}

@RedisPolicyDsl
class RedisPersistencePolicyBuilder {
    lateinit var mode: PersistenceMode
    lateinit var appendFsync: AppendFsync
    lateinit var dataVolume: String

    internal fun build() = RedisPersistencePolicy(mode, appendFsync, dataVolume)
}

@RedisPolicyDsl
class RedisMemoryPolicyBuilder {
    var maxMemoryBytes: Long = 0
    lateinit var evictionPolicy: EvictionPolicy

    internal fun build() = RedisMemoryPolicy(maxMemoryBytes, evictionPolicy)
}

@RedisPolicyDsl
class RedisSecurityPolicyBuilder {
    var tls: Boolean = false
    var authentication: Boolean = false

    internal fun build() = RedisSecurityPolicy(tls, authentication)
}

@RedisPolicyDsl
class RedisClientPolicyBuilder internal constructor(private val service: String) {
    var commandTimeoutMs: Long = 0
    var connectTimeoutMs: Long = 0

    internal fun build() = RedisClientPolicy(service, commandTimeoutMs, connectTimeoutMs)
}

@RedisPolicyDsl
class RedisNamespacePolicyBuilder internal constructor(private val name: String) {
    lateinit var owner: String
    lateinit var pattern: String
    lateinit var kind: NamespaceKind
    var ttlSeconds: Long? = null
    var pruneWindowSeconds: Long? = null
    var readLimit: Int? = null

    internal fun build() = RedisNamespacePolicy(
        name,
        owner,
        pattern,
        kind,
        ttlSeconds,
        pruneWindowSeconds,
        readLimit
    )
}

@RedisPolicyDsl
class RedisLockPolicyBuilder internal constructor(private val name: String) {
    lateinit var owner: String
    lateinit var pattern: String
    var leaseMs: Long = 0
    var maxOperationMs: Long = 0
    var uniqueOwnerToken: Boolean = false
    var compareAndDeleteRelease: Boolean = false

    internal fun build() = RedisLockPolicy(
        name,
        owner,
        pattern,
        leaseMs,
        maxOperationMs,
        uniqueOwnerToken,
        compareAndDeleteRelease
    )
}

fun redisPolicies(block: RedisPoliciesBuilder.() -> Unit): RedisPolicyCatalog =
    RedisPoliciesBuilder().apply(block).build()

object RedisPolicyValidator {
    fun validate(catalog: RedisPolicyCatalog) {
        require(catalog.stores.map { it.name }.distinct().size == catalog.stores.size) {
            "Redis store names must be unique"
        }
        catalog.stores.forEach(::validateStore)
    }

    private fun validateStore(store: RedisStorePolicy) {
        require(store.name.isNotBlank()) { "Redis store name must not be blank" }
        require(store.owner.isNotBlank()) { "${store.name}: owner must not be blank" }
        require(store.memory.maxMemoryBytes > 0) { "${store.name}: max memory must be positive" }
        require(store.clients.map { it.service }.distinct().size == store.clients.size) {
            "${store.name}: client services must be unique"
        }
        store.clients.forEach { client ->
            require(client.service.isNotBlank()) { "${store.name}: client service must not be blank" }
            require(client.commandTimeoutMs > 0 && client.connectTimeoutMs > 0) {
                "${store.name}/${client.service}: Redis timeouts must be positive"
            }
        }
        require(store.namespaces.map { it.name }.distinct().size == store.namespaces.size) {
            "${store.name}: namespace names must be unique"
        }
        require(store.namespaces.map { it.pattern }.distinct().size == store.namespaces.size) {
            "${store.name}: namespace patterns must be unique"
        }
        store.namespaces.forEach { namespace ->
            require(namespace.owner.isNotBlank() && namespace.pattern.isNotBlank()) {
                "${store.name}/${namespace.name}: owner and pattern must be declared"
            }
            require(namespace.ttlSeconds == null || namespace.ttlSeconds > 0) {
                "${store.name}/${namespace.name}: TTL must be positive"
            }
            require(namespace.pruneWindowSeconds == null || namespace.pruneWindowSeconds > 0) {
                "${store.name}/${namespace.name}: prune window must be positive"
            }
            require(namespace.readLimit == null || namespace.readLimit > 0) {
                "${store.name}/${namespace.name}: read limit must be positive"
            }
        }
        require(store.locks.map { it.name }.distinct().size == store.locks.size) {
            "${store.name}: lock names must be unique"
        }
        require(store.locks.map { it.pattern }.distinct().size == store.locks.size) {
            "${store.name}: lock patterns must be unique"
        }
        store.locks.forEach { lock ->
            require(lock.owner.isNotBlank() && lock.pattern.isNotBlank()) {
                "${store.name}/${lock.name}: owner and pattern must be declared"
            }
            require(lock.leaseMs > lock.maxOperationMs && lock.maxOperationMs > 0) {
                "${store.name}/${lock.name}: lock lease must exceed the bounded operation time"
            }
            require(lock.uniqueOwnerToken && lock.compareAndDeleteRelease) {
                "${store.name}/${lock.name}: lock must use owner tokens and compare-and-delete release"
            }
        }

        if (store.role == StoreRole.REBUILDABLE_CACHE_AND_COORDINATION) {
            require(store.persistence.mode == PersistenceMode.AOF &&
                    store.persistence.appendFsync == AppendFsync.EVERY_SECOND &&
                    store.persistence.dataVolume.isNotBlank()) {
                "${store.name}: cache and coordination store requires persistent AOF every second"
            }
            require(store.memory.evictionPolicy == EvictionPolicy.NO_EVICTION) {
                "${store.name}: coordination keys require noeviction"
            }
        }
        if (store.topology == RedisTopology.SINGLE_NODE) {
            require(OperationalRisk.SINGLE_NODE_NO_FAILOVER in store.operationalRisks) {
                "${store.name}: single-node failover risk must be visible"
            }
        }
        if (!store.security.tls || !store.security.authentication) {
            require(OperationalRisk.NO_TRANSPORT_OR_CLIENT_AUTH in store.operationalRisks) {
                "${store.name}: missing Redis transport/auth risk must be visible"
            }
        }
        if (store.memory.evictionPolicy == EvictionPolicy.NO_EVICTION) {
            require(OperationalRisk.NO_EVICTION_REQUIRES_CAPACITY_ALERTS in store.operationalRisks) {
                "${store.name}: noeviction capacity risk must be visible"
            }
        }
    }
}
