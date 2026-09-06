package com.tinder.clone.moderation.infrastructure.persistence

import com.tinder.clone.moderation.application.service.DecisionSaveResult
import com.tinder.clone.moderation.application.service.IdempotencyConflictException
import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import com.tinder.clone.moderation.application.service.StoredModerationDecision
import com.tinder.clone.moderation.application.service.ReviewAction
import com.tinder.clone.moderation.application.service.ReviewLifecycleException
import com.tinder.clone.moderation.application.service.ReviewStatus
import com.tinder.clone.moderation.application.service.ReviewTask
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.infrastructure.http.ContextMessageDto
import com.tinder.clone.moderation.infrastructure.http.EvidenceDto
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.text.Normalizer
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class DurableStorageException(cause: Throwable) : RuntimeException("Durable storage is unavailable", cause)

open class JdbcModerationDecisionStore(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val rawContentRetention: Duration = Duration.ofDays(90)
) : ModerationDecisionStore {
    override fun findByIdempotencyKey(key: String): StoredModerationDecision? = storageCall {
        jdbc.query(
            "$SELECT WHERE idempotency_key = ?",
            RowMapper { rs, _ -> rs.toStored() }, key
        ).singleOrNull()
    }

    @Transactional
    override fun saveOrGet(decision: StoredModerationDecision): DecisionSaveResult = storageCall {
        val request = decision.request
        val response = decision.response
        try {
            jdbc.update(
                """INSERT INTO moderation_decision
                   (decision_id, idempotency_key, request_hash, content_id, content_type, author_id,
                    locale, country, normalized_text, image_urls_json, context_json, decision, reason,
                    confidence, policy_version, evidence_json, created_at, raw_content_expires_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)""",
                response.decisionId, decision.idempotencyKey, decision.requestHash, request.contentId,
                request.contentType.name, request.authorId, request.locale, request.country,
                request.text?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) },
                objectMapper.writeValueAsString(request.imageUrls),
                objectMapper.writeValueAsString(request.conversationContext), response.decision,
                response.reason, response.confidence, response.evidence.policyVersion,
                objectMapper.writeValueAsString(response.evidence), response.createdAt.atOffset(ZoneOffset.UTC),
                response.createdAt.plus(rawContentRetention).atOffset(ZoneOffset.UTC)
            )
            response.reviewTaskId?.let { reviewId ->
                jdbc.update(
                    """INSERT INTO moderation_review_task
                       (review_task_id, decision_id, status, aggregate_version, created_at)
                       VALUES (?, ?, 'OPEN', 0, ?)""",
                    reviewId, response.decisionId, response.createdAt.atOffset(ZoneOffset.UTC)
                )
            }
            DecisionSaveResult.Created(decision)
        } catch (_: DuplicateKeyException) {
            val existing = findByIdempotencyKey(decision.idempotencyKey)
                ?: throw DurableStorageException(IllegalStateException("Duplicate decision was not readable"))
            if (existing.requestHash != decision.requestHash) throw IdempotencyConflictException()
            DecisionSaveResult.Existing(existing)
        }
    }

    override fun get(id: UUID): ModerationResponseDto? = storageCall {
        jdbc.query("$SELECT WHERE decision_id = ?", RowMapper { rs, _ -> rs.toStored() }, id)
            .singleOrNull()?.response
    }

    override fun list(): List<ModerationResponseDto> = storageCall {
        jdbc.query("$SELECT ORDER BY created_at DESC, decision_id DESC LIMIT 100", RowMapper { rs, _ -> rs.toStored() })
            .map { it.response }
    }

    override fun listReviews(status: ReviewStatus?): List<ReviewTask> = storageCall {
        val where = if (status == null) "" else " WHERE status = ?"
        if (status == null) jdbc.query("$REVIEW_SELECT ORDER BY created_at, review_task_id", REVIEW_MAPPER)
        else jdbc.query("$REVIEW_SELECT$where ORDER BY created_at, review_task_id", REVIEW_MAPPER, status.name)
    }

    override fun getReview(id: UUID): ReviewTask? = storageCall {
        jdbc.query("$REVIEW_SELECT WHERE review_task_id = ?", REVIEW_MAPPER, id).singleOrNull()
    }

    @Transactional
    override fun resolveReview(
        id: UUID,
        expectedVersion: Long,
        action: ReviewAction,
        note: String?,
        actor: String
    ): ReviewTask = storageCall {
        val current = getReview(id) ?: throw ReviewLifecycleException("NOT_FOUND", "Review task not found")
        if (current.status != ReviewStatus.OPEN) {
            throw ReviewLifecycleException("REVIEW_ALREADY_RESOLVED", "Review is already resolved")
        }
        val originalDecision = jdbc.queryForObject(
            "SELECT decision FROM moderation_decision WHERE decision_id = ?", String::class.java, current.decisionId
        ) ?: throw ReviewLifecycleException("NOT_FOUND", "Decision not found")
        val status = if (action == ReviewAction.ESCALATE) ReviewStatus.ESCALATED else ReviewStatus.RESOLVED
        val resolution = when (action) {
            ReviewAction.CONFIRM -> originalDecision
            ReviewAction.OVERRIDE_ALLOW -> "ALLOW"
            ReviewAction.OVERRIDE_BLOCK -> "BLOCK"
            ReviewAction.ESCALATE -> null
        }
        val resolvedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val changed = jdbc.update(
            """UPDATE moderation_review_task SET status = ?, resolution = ?, note = ?, assigned_to = ?,
                      aggregate_version = aggregate_version + 1, resolved_at = ?
               WHERE review_task_id = ? AND aggregate_version = ? AND status = 'OPEN'""",
            status.name, resolution, note, actor, resolvedAt, id, expectedVersion
        )
        if (changed != 1) {
            val latest = getReview(id)
            if (latest?.status != ReviewStatus.OPEN) throw ReviewLifecycleException("REVIEW_ALREADY_RESOLVED", "Review is already resolved")
            throw ReviewLifecycleException("VERSION_CONFLICT", "Resource changed")
        }
        jdbc.update(
            """INSERT INTO moderation_audit_log
               (audit_id, actor, action, target_type, target_id, outcome, details_json, occurred_at)
               VALUES (?, ?, 'RESOLVE_REVIEW', 'REVIEW_TASK', ?, 'SUCCESS', ?::jsonb, ?)""",
            UUID.randomUUID(), actor, id.toString(),
            objectMapper.writeValueAsString(mapOf("action" to action.name, "resolution" to resolution)), resolvedAt
        )
        getReview(id)!!
    }

    private fun java.sql.ResultSet.toStored(): StoredModerationDecision {
        val evidence = objectMapper.readValue(getString("evidence_json"), EvidenceDto::class.java)
        val context = objectMapper.readValue(
            getString("context_json"), object : TypeReference<List<ContextMessageDto>>() {}
        )
        val images = objectMapper.readValue(
            getString("image_urls_json"), object : TypeReference<List<String>>() {}
        )
        val createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant()
        val response = ModerationResponseDto(
            getObject("decision_id", UUID::class.java), getString("content_id"), getString("decision"),
            getString("reason"), getObject("confidence") as Double?, evidence,
            reviewTaskId = getObject("review_task_id", UUID::class.java), replayed = false, createdAt = createdAt
        )
        val request = ModerationRequestDto(
            getString("content_id"), ContentType.valueOf(getString("content_type")),
            getString("normalized_text"), images, getString("locale"), getString("country"),
            getString("author_id"), context
        )
        return StoredModerationDecision(getString("idempotency_key"), getString("request_hash"), request, response)
    }

    private fun <T> storageCall(block: () -> T): T = try {
        block()
    } catch (error: IdempotencyConflictException) {
        throw error
    } catch (error: DataAccessException) {
        throw DurableStorageException(error)
    }

    private companion object {
        const val SELECT = """SELECT decision_id, idempotency_key, request_hash, content_id, content_type,
            author_id, locale, country, normalized_text, image_urls_json::text, context_json::text,
            decision, reason, confidence, evidence_json::text, created_at,
            (SELECT review_task_id FROM moderation_review_task r WHERE r.decision_id = moderation_decision.decision_id) review_task_id
            FROM moderation_decision"""
        const val REVIEW_SELECT = """SELECT review_task_id, decision_id, status, resolution, note,
            assigned_to, aggregate_version, created_at, resolved_at FROM moderation_review_task"""
        val REVIEW_MAPPER = RowMapper { rs: java.sql.ResultSet, _: Int ->
            ReviewTask(
                rs.getObject("review_task_id", UUID::class.java), rs.getObject("decision_id", UUID::class.java),
                ReviewStatus.valueOf(rs.getString("status")), rs.getString("resolution"), rs.getString("note"),
                rs.getString("assigned_to"), rs.getLong("aggregate_version"),
                rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                rs.getObject("resolved_at", OffsetDateTime::class.java)?.toInstant()
            )
        }
    }
}
