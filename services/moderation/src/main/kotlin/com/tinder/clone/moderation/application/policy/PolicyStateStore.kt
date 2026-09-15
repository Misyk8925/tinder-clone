package com.tinder.clone.moderation.application.policy

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.policy.CategoryThreshold
import java.time.Instant
import java.util.UUID

interface PolicyStateStore {
    fun loadPolicies(): List<PolicyDocument>
    fun loadActivations(): List<PolicyActivation>
    fun insertPolicy(document: PolicyDocument, actor: String)
    fun updatePolicy(document: PolicyDocument, expectedVersion: Long, actor: String)
    fun upsertActivation(
        activation: PolicyActivation,
        expectedVersion: Long?,
        actor: String,
        action: String = "ACTIVATE_POLICY"
    )
}

data class AuditEntry(
    val actor: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val outcome: String,
    val occurredAt: Instant
)

class InMemoryPolicyStateStore(
    private val outbox: com.tinder.clone.moderation.application.ports.ModerationOutboxPort =
        com.tinder.clone.moderation.application.ports.NoOpModerationOutbox()
) : PolicyStateStore {
    private val policies = linkedMapOf<String, PolicyDocument>()
    private val activations = linkedMapOf<Pair<ContentType?, String?>, PolicyActivation>()
    private val audits = mutableListOf<AuditEntry>()

    init {
        seedDefaults()
    }

    @Synchronized override fun loadPolicies() = policies.values.toList()
    @Synchronized override fun loadActivations() = activations.values.toList()
    @Synchronized fun auditLog(): List<AuditEntry> = audits.toList()

    @Synchronized
    override fun insertPolicy(document: PolicyDocument, actor: String) {
        if (policies.putIfAbsent(document.version, document) != null) {
            throw PolicyLifecycleException("POLICY_VERSION_EXISTS", "Policy version already exists")
        }
        audit(actor, "CREATE_POLICY", "POLICY", document.version, document.createdAt)
    }

    @Synchronized
    override fun updatePolicy(document: PolicyDocument, expectedVersion: Long, actor: String) {
        val current = policies[document.version] ?: throw PolicyLifecycleException("NOT_FOUND", "Policy not found")
        if (current.aggregateVersion != expectedVersion) throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
        policies[document.version] = document
        audit(
            actor,
            if (document.status == PolicyStatus.PUBLISHED) "PUBLISH_POLICY" else "UPDATE_POLICY",
            "POLICY",
            document.version,
            document.publishedAt ?: Instant.now()
        )
        if (document.status == PolicyStatus.PUBLISHED) {
            outbox.enqueuePolicyChanged(document.version, "PUBLISHED", null, null, null)
        }
    }

    @Synchronized
    override fun upsertActivation(
        activation: PolicyActivation,
        expectedVersion: Long?,
        actor: String,
        action: String
    ) {
        val key = activation.contentType to activation.locale?.lowercase()
        val current = activations[key]
        if (expectedVersion != null && current?.aggregateVersion != expectedVersion) {
            throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
        }
        activations[key] = activation
        audit(actor, action, "POLICY_ACTIVATION", activation.activationId.toString(), activation.activatedAt)
        outbox.enqueuePolicyChanged(
            activation.version,
            if (action == "ROLLBACK_POLICY") "ROLLED_BACK" else "ACTIVATED",
            activation.contentType,
            activation.locale,
            activation.previousVersion
        )
    }

    private fun audit(actor: String, action: String, targetType: String, targetId: String, at: Instant) {
        audits += AuditEntry(actor, action, targetType, targetId, "SUCCESS", at)
    }

    private fun seedDefaults() {
        val epoch = Instant.parse("1970-01-01T00:00:00Z")
        val publishedAt = Instant.parse("2026-09-06T00:00:00Z")
        policies["unconfigured"] = PolicyDocument(
            "unconfigured", "System fallback: no moderation thresholds are configured", emptyList(),
            PolicyStatus.PUBLISHED, 0, epoch, epoch
        )
        policies["tinder-default-v1"] = PolicyDocument(
            "tinder-default-v1",
            "Default Tinder thresholds for profile, photo, message, and report content",
            listOf(
                PolicyScopeDefinition(
                    null, null, mapOf(
                        ModerationCategory.HARASSMENT to CategoryThreshold(0.55, 0.85),
                        ModerationCategory.HARASSMENT_THREATENING to CategoryThreshold(0.35, 0.70),
                        ModerationCategory.HATE to CategoryThreshold(0.50, 0.80),
                        ModerationCategory.HATE_THREATENING to CategoryThreshold(0.30, 0.65),
                        ModerationCategory.SEXUAL_CONTENT to CategoryThreshold(0.60, 0.90),
                        ModerationCategory.SEXUAL_MINORS to CategoryThreshold(0.10, 0.25),
                        ModerationCategory.SELF_HARM to CategoryThreshold(0.35, 0.70),
                        ModerationCategory.VIOLENCE to CategoryThreshold(0.45, 0.80)
                    )
                )
            ),
            PolicyStatus.PUBLISHED, 0, publishedAt, publishedAt
        )
        activations[null to null] = PolicyActivation(
            UUID.fromString("00000000-0000-0000-0000-00000000a001"),
            "tinder-default-v1",
            "unconfigured",
            null,
            null,
            0,
            publishedAt
        )
    }
}
