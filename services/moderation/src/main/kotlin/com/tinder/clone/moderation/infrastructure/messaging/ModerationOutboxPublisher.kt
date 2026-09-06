package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.config.ModerationKafkaProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class ModerationOutboxPublisher(
    private val jdbc: JdbcTemplate,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafka: ModerationKafkaProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${moderation.kafka.outbox-poll-ms:2000}")
    fun publishPending() {
        if (!kafka.enabled) return
        val rows = jdbc.query(
            """SELECT outbox_id, topic, message_key, payload_json, attempt_count
               FROM moderation_outbox
               WHERE published_at IS NULL AND next_attempt_at <= now()
               ORDER BY created_at
               LIMIT 50"""
        ) { rs, _ ->
            OutboxRow(
                UUID.fromString(rs.getString("outbox_id")),
                rs.getString("topic"),
                rs.getString("message_key"),
                rs.getString("payload_json"),
                rs.getInt("attempt_count")
            )
        }
        rows.forEach(::publishOne)
    }

    private fun publishOne(row: OutboxRow) {
        try {
            kafkaTemplate.send(row.topic, row.messageKey, row.payload).get()
            jdbc.update(
                "UPDATE moderation_outbox SET published_at = now(), last_error = NULL WHERE outbox_id = ?",
                row.id
            )
        } catch (error: Exception) {
            val attempts = row.attempts + 1
            val retryAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds((attempts * 2L).coerceAtMost(60))
            jdbc.update(
                """UPDATE moderation_outbox
                   SET attempt_count = ?, next_attempt_at = ?, last_error = ?
                   WHERE outbox_id = ?""",
                attempts,
                retryAt,
                error.message?.take(500),
                row.id
            )
            log.warn("Failed to publish moderation outbox {} (attempt {})", row.id, attempts, error)
        }
    }

    private data class OutboxRow(
        val id: UUID,
        val topic: String,
        val messageKey: String,
        val payload: String,
        val attempts: Int
    )
}
