package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.application.ports.ModerationOutboxPort
import com.tinder.clone.moderation.config.ModerationKafkaProperties
import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class JdbcModerationOutbox(
    private val jdbc: JdbcTemplate,
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
        jdbc.update(
            """INSERT INTO moderation_outbox
               (outbox_id, aggregate_type, aggregate_id, topic, message_key, payload_json,
                created_at, attempt_count, next_attempt_at)
               VALUES (?, 'ModerationDecision', ?, ?, ?, ?::jsonb, ?, 0, ?)""",
            UUID.randomUUID(),
            response.decisionId.toString(),
            kafka.resultsTopic,
            request.contentId,
            objectMapper.writeValueAsString(payload),
            occurredAt.atOffset(ZoneOffset.UTC),
            occurredAt.atOffset(ZoneOffset.UTC)
        )
    }
}
