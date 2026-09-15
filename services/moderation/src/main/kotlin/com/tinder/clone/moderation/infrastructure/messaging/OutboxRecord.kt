package com.tinder.clone.moderation.infrastructure.messaging

import java.time.Instant
import java.util.UUID

data class OutboxRecord(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val topic: String,
    val messageKey: String,
    val payload: String,
    val createdAt: Instant,
    val publishedAt: Instant? = null,
    val attemptCount: Int = 0,
    val nextAttemptAt: Instant,
    val lastError: String? = null
)

interface OutboxRecordRepository {
    fun insert(record: OutboxRecord)
    fun due(now: Instant, limit: Int = 50): List<OutboxRecord>
    fun markPublished(id: UUID, at: Instant)
    fun markFailed(id: UUID, attemptCount: Int, nextAttemptAt: Instant, error: String?)
    fun unpublishedCount(): Long
}

class InMemoryOutboxRecordRepository : OutboxRecordRepository {
    private val records = linkedMapOf<UUID, OutboxRecord>()

    @Synchronized
    override fun insert(record: OutboxRecord) {
        records[record.id] = record
    }

    @Synchronized
    override fun due(now: Instant, limit: Int): List<OutboxRecord> =
        records.values
            .filter { it.publishedAt == null && !it.nextAttemptAt.isAfter(now) }
            .sortedBy { it.createdAt }
            .take(limit)

    @Synchronized
    override fun markPublished(id: UUID, at: Instant) {
        records[id]?.let { records[id] = it.copy(publishedAt = at, lastError = null) }
    }

    @Synchronized
    override fun markFailed(id: UUID, attemptCount: Int, nextAttemptAt: Instant, error: String?) {
        records[id]?.let {
            records[id] = it.copy(attemptCount = attemptCount, nextAttemptAt = nextAttemptAt, lastError = error)
        }
    }

    @Synchronized
    override fun unpublishedCount(): Long = records.values.count { it.publishedAt == null }.toLong()

    @Synchronized
    fun all(): List<OutboxRecord> = records.values.toList()
}

fun interface EventPublisher {
    fun send(topic: String, key: String, payload: String)
}