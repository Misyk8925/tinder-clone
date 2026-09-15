package com.tinder.clone.moderation.infrastructure.messaging

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
@ConditionalOnProperty(prefix = "moderation.kafka", name = ["enabled"], havingValue = "true")
class ModerationOutboxPublisher(
    private val records: OutboxRecordRepository,
    private val publisher: EventPublisher,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${moderation.kafka.outbox-poll-ms:2000}")
    fun publishPending() {
        records.due(clock.instant()).forEach(::publishOne)
    }

    fun publishOne(row: OutboxRecord) {
        try {
            publisher.send(row.topic, row.messageKey, row.payload)
            records.markPublished(row.id, clock.instant())
        } catch (error: Exception) {
            val attempts = row.attemptCount + 1
            val retryAt = clock.instant().plus(Duration.ofSeconds((attempts * 2L).coerceAtMost(60)))
            records.markFailed(row.id, attempts, retryAt, error.message?.take(500))
            log.warn("Failed to publish moderation outbox {} (attempt {})", row.id, attempts)
        }
    }
}