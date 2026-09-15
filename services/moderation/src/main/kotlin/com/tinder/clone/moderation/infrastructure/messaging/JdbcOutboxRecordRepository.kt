package com.tinder.clone.moderation.infrastructure.messaging

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class JdbcOutboxRecordRepository(
    private val jdbc: JdbcTemplate
) : OutboxRecordRepository {
    override fun insert(record: OutboxRecord) {
        jdbc.update(
            """INSERT INTO moderation_outbox
               (outbox_id, aggregate_type, aggregate_id, topic, message_key, payload_json,
                created_at, attempt_count, next_attempt_at)
               VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, 0, ?)""",
            record.id,
            record.aggregateType,
            record.aggregateId,
            record.topic,
            record.messageKey,
            record.payload,
            record.createdAt.atOffset(ZoneOffset.UTC),
            record.nextAttemptAt.atOffset(ZoneOffset.UTC)
        )
    }

    override fun due(now: Instant, limit: Int): List<OutboxRecord> = jdbc.query(
        """SELECT outbox_id, aggregate_type, aggregate_id, topic, message_key, payload_json,
                  created_at, published_at, attempt_count, next_attempt_at, last_error
           FROM moderation_outbox
           WHERE published_at IS NULL AND next_attempt_at <= ?
           ORDER BY created_at
           LIMIT ?""",
        MAPPER,
        now.atOffset(ZoneOffset.UTC),
        limit
    )

    override fun markPublished(id: UUID, at: Instant) {
        jdbc.update(
            "UPDATE moderation_outbox SET published_at = ?, last_error = NULL WHERE outbox_id = ?",
            at.atOffset(ZoneOffset.UTC),
            id
        )
    }

    override fun markFailed(id: UUID, attemptCount: Int, nextAttemptAt: Instant, error: String?) {
        jdbc.update(
            """UPDATE moderation_outbox
               SET attempt_count = ?, next_attempt_at = ?, last_error = ?
               WHERE outbox_id = ?""",
            attemptCount,
            nextAttemptAt.atOffset(ZoneOffset.UTC),
            error,
            id
        )
    }

    override fun unpublishedCount(): Long =
        jdbc.queryForObject(
            "SELECT count(*) FROM moderation_outbox WHERE published_at IS NULL",
            Long::class.java
        ) ?: 0

    private companion object {
        val MAPPER = RowMapper { rs, _ ->
            OutboxRecord(
                id = UUID.fromString(rs.getString("outbox_id")),
                aggregateType = rs.getString("aggregate_type"),
                aggregateId = rs.getString("aggregate_id"),
                topic = rs.getString("topic"),
                messageKey = rs.getString("message_key"),
                payload = rs.getString("payload_json"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                publishedAt = rs.getObject("published_at", OffsetDateTime::class.java)?.toInstant(),
                attemptCount = rs.getInt("attempt_count"),
                nextAttemptAt = rs.getObject("next_attempt_at", OffsetDateTime::class.java).toInstant(),
                lastError = rs.getString("last_error")
            )
        }
    }
}