package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.policy.PolicyActivation
import com.tinder.clone.moderation.application.policy.PolicyDocument
import com.tinder.clone.moderation.application.policy.PolicyLifecycleException
import com.tinder.clone.moderation.application.policy.PolicyScopeDefinition
import com.tinder.clone.moderation.application.policy.PolicyStateStore
import com.tinder.clone.moderation.application.policy.PolicyStatus
import com.tinder.clone.moderation.domain.model.ContentType
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

open class JdbcPolicyStateStore(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) : PolicyStateStore {
    private data class PolicyPayload(val scopes: List<PolicyScopeDefinition> = emptyList())

    override fun loadPolicies(): List<PolicyDocument> = jdbc.query(
        """SELECT policy_version, status, description, policy_json::text, aggregate_version,
                  created_at, published_at FROM moderation_policy_version""",
        RowMapper { rs, _ -> rs.toPolicy() }
    )

    override fun loadActivations(): List<PolicyActivation> = jdbc.query(
        """SELECT activation_id, policy_version, previous_policy_version, content_type,
                  locale, aggregate_version, activated_at FROM moderation_policy_activation""",
        RowMapper { rs, _ -> rs.toActivation() }
    )

    @Transactional
    override fun insertPolicy(document: PolicyDocument, actor: String) {
        try {
            jdbc.update(
                """INSERT INTO moderation_policy_version
                   (policy_version, status, description, policy_json, aggregate_version, created_by, created_at)
                   VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)""",
                document.version, document.status.name, document.description,
                objectMapper.writeValueAsString(PolicyPayload(document.scopes)),
                document.aggregateVersion, actor, document.createdAt.atOffset(java.time.ZoneOffset.UTC)
            )
            audit(actor, "CREATE_POLICY", "POLICY", document.version, document.createdAt)
        } catch (_: DuplicateKeyException) {
            throw PolicyLifecycleException("POLICY_VERSION_EXISTS", "Policy version already exists")
        }
    }

    @Transactional
    override fun updatePolicy(document: PolicyDocument, expectedVersion: Long, actor: String) {
        val changed = jdbc.update(
            """UPDATE moderation_policy_version SET status = ?, description = ?, policy_json = ?::jsonb,
                      aggregate_version = ?, published_by = ?, published_at = ?
               WHERE policy_version = ? AND aggregate_version = ?""",
            document.status.name, document.description,
            objectMapper.writeValueAsString(PolicyPayload(document.scopes)), document.aggregateVersion,
            if (document.status == PolicyStatus.PUBLISHED) actor else null,
            document.publishedAt?.atOffset(java.time.ZoneOffset.UTC),
            document.version, expectedVersion
        )
        if (changed != 1) throw PolicyLifecycleException("VERSION_CONFLICT", "Resource changed")
        audit(
            actor,
            if (document.status == PolicyStatus.PUBLISHED) "PUBLISH_POLICY" else "UPDATE_POLICY",
            "POLICY", document.version, document.publishedAt ?: Instant.now()
        )
    }

    @Transactional
    override fun upsertActivation(activation: PolicyActivation, expectedVersion: Long?, actor: String) {
        if (expectedVersion == null) {
            try {
                jdbc.update(
                    """INSERT INTO moderation_policy_activation
                       (activation_id, content_type, locale, policy_version, previous_policy_version,
                        aggregate_version, activated_by, activated_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    activation.activationId, activation.contentType?.name, activation.locale?.lowercase(),
                    activation.version, activation.previousVersion, activation.aggregateVersion, actor,
                    activation.activatedAt.atOffset(java.time.ZoneOffset.UTC)
                )
                audit(actor, "ACTIVATE_POLICY", "POLICY_ACTIVATION", activation.activationId.toString(), activation.activatedAt)
                return
            } catch (_: DuplicateKeyException) {
                throw PolicyLifecycleException("VERSION_CONFLICT", "Activation changed")
            }
        }
        val changed = jdbc.update(
            """UPDATE moderation_policy_activation
               SET policy_version = ?, previous_policy_version = ?, aggregate_version = ?,
                   activated_by = ?, activated_at = ?
               WHERE activation_id = ? AND aggregate_version = ?""",
            activation.version, activation.previousVersion, activation.aggregateVersion,
            actor, activation.activatedAt.atOffset(java.time.ZoneOffset.UTC), activation.activationId, expectedVersion
        )
        if (changed != 1) throw PolicyLifecycleException("VERSION_CONFLICT", "Activation changed")
        audit(actor, "UPDATE_POLICY_ACTIVATION", "POLICY_ACTIVATION", activation.activationId.toString(), activation.activatedAt)
    }

    private fun audit(actor: String, action: String, targetType: String, targetId: String, at: Instant) {
        jdbc.update(
            """INSERT INTO moderation_audit_log
               (audit_id, actor, action, target_type, target_id, outcome, occurred_at)
               VALUES (?, ?, ?, ?, ?, 'SUCCESS', ?)""",
            UUID.randomUUID(), actor, action, targetType, targetId, at.atOffset(java.time.ZoneOffset.UTC)
        )
    }

    private fun ResultSet.toPolicy(): PolicyDocument {
        val payload = objectMapper.readValue(getString("policy_json"), PolicyPayload::class.java)
        return PolicyDocument(
            getString("policy_version"), getString("description"), payload.scopes,
            PolicyStatus.valueOf(getString("status")), getLong("aggregate_version"),
            getObject("created_at", java.time.OffsetDateTime::class.java).toInstant(),
            getObject("published_at", java.time.OffsetDateTime::class.java)?.toInstant()
        )
    }

    private fun ResultSet.toActivation() = PolicyActivation(
        getObject("activation_id", UUID::class.java), getString("policy_version"),
        getString("previous_policy_version"), getString("content_type")?.let(ContentType::valueOf),
        getString("locale"), getLong("aggregate_version"),
        getObject("activated_at", java.time.OffsetDateTime::class.java).toInstant()
    )
}
