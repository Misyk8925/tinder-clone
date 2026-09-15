package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.application.ports.ModerationOutboxPort
import com.tinder.clone.moderation.config.ModerationKafkaProperties
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "moderation.persistence",
    name = ["mode"],
    havingValue = "jdbc",
    matchIfMissing = true
)
class JdbcModerationOutbox(
    private val records: OutboxRecordRepository,
    private val objectMapper: ObjectMapper,
    private val kafka: ModerationKafkaProperties,
    private val clock: Clock = Clock.systemUTC()
) : ModerationOutboxPort {
    override fun enqueueCompleted(
        request: ModerationRequestDto,
        response: ModerationResponseDto,
        requestMessageId: UUID?
    ) {
        val occurredAt = clock.instant()
        val payload = ModerationCompletedEvent(
            messageId = UUID.randomUUID(),
            correlationId = request.contentId,
            occurredAt = occurredAt,
            requestMessageId = requestMessageId,
            decisionId = response.decisionId,
            contentId = request.contentId,
            decision = response.decision,
            reason = response.reason,
            policyVersion = response.evidence.policyVersion,
            reviewTaskId = response.reviewTaskId,
            evidence = mapOf(
                "provider" to response.evidence.provider,
                "model" to response.evidence.model,
                "policyVersion" to response.evidence.policyVersion
            )
        )
        insert(
            aggregateType = "ModerationDecision",
            aggregateId = response.decisionId.toString(),
            topic = kafka.resultsTopic,
            messageKey = request.contentId,
            payload = payload,
            at = occurredAt
        )
        response.reviewTaskId?.let { reviewId ->
            enqueueReviewChanged(reviewId, response.decisionId, "OPEN", null, 0)
        }
    }

    override fun enqueueReviewChanged(
        reviewTaskId: UUID,
        decisionId: UUID,
        status: String,
        resolution: String?,
        aggregateVersion: Long
    ) {
        val occurredAt = clock.instant()
        val payload = ReviewChangedEvent(
            messageId = UUID.randomUUID(),
            correlationId = reviewTaskId.toString(),
            occurredAt = occurredAt,
            reviewTaskId = reviewTaskId,
            decisionId = decisionId,
            status = status,
            resolution = resolution,
            aggregateVersion = aggregateVersion
        )
        insert(
            aggregateType = "ReviewTask",
            aggregateId = reviewTaskId.toString(),
            topic = kafka.reviewsTopic,
            messageKey = reviewTaskId.toString(),
            payload = payload,
            at = occurredAt
        )
    }

    override fun enqueuePolicyChanged(
        policyVersion: String,
        changeType: String,
        contentType: ContentType?,
        locale: String?,
        previousVersion: String?
    ) {
        val occurredAt = clock.instant()
        val payload = PolicyChangedEvent(
            messageId = UUID.randomUUID(),
            correlationId = policyVersion,
            occurredAt = occurredAt,
            policyVersion = policyVersion,
            changeType = changeType,
            contentType = contentType?.name,
            locale = locale,
            previousVersion = previousVersion
        )
        insert(
            aggregateType = "Policy",
            aggregateId = policyVersion,
            topic = kafka.policiesTopic,
            messageKey = policyVersion,
            payload = payload,
            at = occurredAt
        )
    }

    private fun insert(
        aggregateType: String,
        aggregateId: String,
        topic: String,
        messageKey: String,
        payload: Any,
        at: java.time.Instant
    ) {
        records.insert(
            OutboxRecord(
                id = UUID.randomUUID(),
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                topic = topic,
                messageKey = messageKey,
                payload = objectMapper.writeValueAsString(payload),
                createdAt = at,
                nextAttemptAt = at
            )
        )
    }
}