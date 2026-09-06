package com.tinder.clone.moderation.application.policy

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.policy.CategoryThreshold
import com.tinder.clone.moderation.domain.policy.ModerationPolicy
import com.tinder.clone.moderation.domain.policy.ModerationPolicyCatalog
import com.tinder.clone.moderation.domain.policy.ModerationPolicyConfig
import com.tinder.clone.moderation.domain.ModerationPolicyProvider
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class PolicyStatus { DRAFT, PUBLISHED }

data class PolicyScopeDefinition(
    val contentType: ContentType?,
    val locale: String?,
    val thresholds: Map<ModerationCategory, CategoryThreshold>
)

data class PolicyDocument(
    val version: String,
    val description: String?,
    val scopes: List<PolicyScopeDefinition>,
    val status: PolicyStatus,
    val aggregateVersion: Long,
    val createdAt: Instant,
    val publishedAt: Instant? = null
)

data class PolicyActivation(
    val activationId: UUID,
    val version: String,
    val previousVersion: String?,
    val contentType: ContentType?,
    val locale: String?,
    val aggregateVersion: Long,
    val activatedAt: Instant
)

class PolicyLifecycleException(val code: String, message: String) : RuntimeException(message)

class RuntimePolicyRegistry(
    private val clock: Clock = Clock.systemUTC(),
    private val store: PolicyStateStore = InMemoryPolicyStateStore()
) : ModerationPolicyProvider {
    private val policies = linkedMapOf<String, PolicyDocument>()
    private val activations = linkedMapOf<Pair<ContentType?, String?>, PolicyActivation>()

    init { refresh() }

    @Synchronized
    fun refresh() {
        policies.clear()
        policies.putAll(store.loadPolicies().associateBy { it.version })
        activations.clear()
        activations.putAll(store.loadActivations().associateBy { it.contentType to it.locale?.lowercase() })
    }

    @Synchronized
    fun create(version: String, description: String?, scopes: List<PolicyScopeDefinition>, actor: String = "system"): PolicyDocument {
        if (policies.containsKey(version)) throw PolicyLifecycleException("POLICY_VERSION_EXISTS", "Policy version already exists")
        validateVersion(version)
        val document = PolicyDocument(version, description, scopes, PolicyStatus.DRAFT, 0, clock.instant())
        store.insertPolicy(document, actor)
        policies[version] = document
        return document
    }

    @Synchronized
    fun get(version: String): PolicyDocument {
        refresh()
        return policies[version] ?: throw PolicyLifecycleException("NOT_FOUND", "Policy not found")
    }

    @Synchronized
    fun list(): List<PolicyDocument> {
        refresh()
        return policies.values.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun replaceDraft(
        version: String,
        expectedVersion: Long,
        description: String?,
        scopes: List<PolicyScopeDefinition>,
        actor: String = "system"
    ): PolicyDocument {
        val current = get(version)
        requireVersion(current.aggregateVersion, expectedVersion)
        if (current.status == PolicyStatus.PUBLISHED) {
            throw PolicyLifecycleException("PUBLISHED_POLICY_IMMUTABLE", "Published policy is immutable")
        }
        return current.copy(
            description = description,
            scopes = scopes,
            aggregateVersion = current.aggregateVersion + 1
        ).also {
            store.updatePolicy(it, expectedVersion, actor)
            policies[version] = it
        }
    }

    fun validation(version: String): List<String> = validate(get(version))

    @Synchronized
    fun publish(version: String, expectedVersion: Long, actor: String = "system"): PolicyDocument {
        val current = get(version)
        requireVersion(current.aggregateVersion, expectedVersion)
        if (current.status == PolicyStatus.PUBLISHED) return current
        val errors = validate(current)
        if (errors.isNotEmpty()) throw PolicyLifecycleException("POLICY_INVALID", errors.joinToString("; "))
        return current.copy(
            status = PolicyStatus.PUBLISHED,
            aggregateVersion = current.aggregateVersion + 1,
            publishedAt = clock.instant()
        ).also {
            store.updatePolicy(it, expectedVersion, actor)
            policies[version] = it
        }
    }

    @Synchronized
    fun activate(version: String, contentType: ContentType?, locale: String?, actor: String = "system"): PolicyActivation {
        val policy = get(version)
        if (policy.status != PolicyStatus.PUBLISHED) {
            throw PolicyLifecycleException("POLICY_NOT_PUBLISHED", "Only a published policy can be activated")
        }
        if (locale != null && contentType == null) {
            throw PolicyLifecycleException("INVALID_PAYLOAD", "Locale activation requires content type")
        }
        val key = contentType to locale?.lowercase()
        val previous = activations[key]
        return PolicyActivation(
            activationId = previous?.activationId ?: UUID.randomUUID(),
            version = version,
            previousVersion = previous?.version,
            contentType = contentType,
            locale = locale,
            aggregateVersion = (previous?.aggregateVersion ?: -1) + 1,
            activatedAt = clock.instant()
        ).also {
            store.upsertActivation(it, previous?.aggregateVersion, actor)
            activations[key] = it
        }
    }

    @Synchronized
    fun rollback(activationId: UUID, expectedVersion: Long, actor: String = "system"): PolicyActivation {
        val entry = activations.entries.firstOrNull { it.value.activationId == activationId }
            ?: throw PolicyLifecycleException("NOT_FOUND", "Activation not found")
        val current = entry.value
        requireVersion(current.aggregateVersion, expectedVersion)
        val previous = current.previousVersion
            ?: throw PolicyLifecycleException("NO_PREVIOUS_POLICY", "No previous policy to restore")
        return current.copy(
            version = previous,
            previousVersion = current.version,
            aggregateVersion = current.aggregateVersion + 1,
            activatedAt = clock.instant()
        ).also {
            store.upsertActivation(it, expectedVersion, actor)
            activations[entry.key] = it
        }
    }

    @Synchronized
    override fun policyFor(content: ModerationContent): ModerationPolicy {
        refresh()
        val activation = activations[content.type to content.locale?.lowercase()]
            ?: activations[content.type to null]
            ?: activations[null to null]
            ?: return emptyPolicy()
        val document = get(activation.version)
        val configs = document.scopes.map {
            ModerationPolicyConfig(document.version, it.contentType, it.locale, it.thresholds)
        }
        return ModerationPolicy(ModerationPolicyCatalog(configs), document.version)
    }

    private fun emptyPolicy() = ModerationPolicy(ModerationPolicyCatalog(emptyList()), "unconfigured")

    private fun validate(document: PolicyDocument): List<String> = buildList {
        if (document.scopes.isEmpty()) add("At least one policy scope is required")
        val keys = document.scopes.map { it.contentType to it.locale?.lowercase() }
        if (keys.size != keys.distinct().size) add("Policy scopes must be unique")
        document.scopes.forEachIndexed { index, scope ->
            if (scope.locale != null && scope.contentType == null) add("Scope $index: locale requires content type")
            if (scope.thresholds.isEmpty()) add("Scope $index: at least one category threshold is required")
        }
    }

    private fun validateVersion(version: String) {
        if (!version.matches(Regex("[A-Za-z0-9._-]{1,80}"))) {
            throw PolicyLifecycleException("INVALID_PAYLOAD", "Invalid policy version")
        }
    }

    private fun requireVersion(actual: Long, expected: Long) {
        if (actual != expected) throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
    }
}
