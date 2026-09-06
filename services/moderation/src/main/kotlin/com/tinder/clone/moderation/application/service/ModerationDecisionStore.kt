package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant

enum class ReviewStatus { OPEN, RESOLVED, ESCALATED }
enum class ReviewAction { CONFIRM, OVERRIDE_ALLOW, OVERRIDE_BLOCK, ESCALATE }

data class ReviewTask(
    val reviewTaskId: UUID,
    val decisionId: UUID,
    val status: ReviewStatus,
    val resolution: String? = null,
    val note: String? = null,
    val assignedTo: String? = null,
    val aggregateVersion: Long = 0,
    val createdAt: Instant,
    val resolvedAt: Instant? = null
)

class ReviewLifecycleException(val code: String, message: String) : RuntimeException(message)

data class StoredModerationDecision(
    val idempotencyKey: String,
    val requestHash: String,
    val request: ModerationRequestDto,
    val response: ModerationResponseDto
)

sealed interface DecisionSaveResult {
    data class Created(val decision: StoredModerationDecision) : DecisionSaveResult
    data class Existing(val decision: StoredModerationDecision) : DecisionSaveResult
}

interface ModerationDecisionStore {
    fun findByIdempotencyKey(key: String): StoredModerationDecision?
    fun saveOrGet(decision: StoredModerationDecision): DecisionSaveResult
    fun get(id: UUID): ModerationResponseDto?
    fun list(): List<ModerationResponseDto>
    fun listReviews(status: ReviewStatus? = null): List<ReviewTask>
    fun getReview(id: UUID): ReviewTask?
    fun resolveReview(id: UUID, expectedVersion: Long, action: ReviewAction, note: String?, actor: String): ReviewTask
}

class InMemoryModerationDecisionStore : ModerationDecisionStore {
    private val byKey = ConcurrentHashMap<String, StoredModerationDecision>()
    private val byId = ConcurrentHashMap<UUID, ModerationResponseDto>()
    private val reviews = ConcurrentHashMap<UUID, ReviewTask>()

    override fun findByIdempotencyKey(key: String) = byKey[key]

    @Synchronized
    override fun saveOrGet(decision: StoredModerationDecision): DecisionSaveResult {
        byKey[decision.idempotencyKey]?.let { return DecisionSaveResult.Existing(it) }
        byKey[decision.idempotencyKey] = decision
        byId[decision.response.decisionId] = decision.response
        decision.response.reviewTaskId?.let { reviewId ->
            reviews[reviewId] = ReviewTask(reviewId, decision.response.decisionId, ReviewStatus.OPEN, createdAt = decision.response.createdAt)
        }
        return DecisionSaveResult.Created(decision)
    }

    override fun get(id: UUID) = byId[id]
    override fun list() = byId.values.sortedByDescending { it.createdAt }
    override fun listReviews(status: ReviewStatus?) = reviews.values
        .filter { status == null || it.status == status }.sortedBy { it.createdAt }
    override fun getReview(id: UUID) = reviews[id]

    @Synchronized
    override fun resolveReview(id: UUID, expectedVersion: Long, action: ReviewAction, note: String?, actor: String): ReviewTask {
        val current = reviews[id] ?: throw ReviewLifecycleException("NOT_FOUND", "Review task not found")
        if (current.status != ReviewStatus.OPEN) throw ReviewLifecycleException("REVIEW_ALREADY_RESOLVED", "Review is already resolved")
        if (current.aggregateVersion != expectedVersion) throw ReviewLifecycleException("VERSION_CONFLICT", "Resource changed")
        val original = byId[current.decisionId] ?: throw ReviewLifecycleException("NOT_FOUND", "Decision not found")
        val updated = current.copy(
            status = if (action == ReviewAction.ESCALATE) ReviewStatus.ESCALATED else ReviewStatus.RESOLVED,
            resolution = when (action) {
                ReviewAction.CONFIRM -> original.decision
                ReviewAction.OVERRIDE_ALLOW -> "ALLOW"
                ReviewAction.OVERRIDE_BLOCK -> "BLOCK"
                ReviewAction.ESCALATE -> null
            },
            note = note,
            assignedTo = actor,
            aggregateVersion = current.aggregateVersion + 1,
            resolvedAt = Instant.now()
        )
        reviews[id] = updated
        return updated
    }
}
