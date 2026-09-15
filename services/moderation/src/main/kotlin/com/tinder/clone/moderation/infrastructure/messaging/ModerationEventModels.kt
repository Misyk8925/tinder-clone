package com.tinder.clone.moderation.infrastructure.messaging

import com.tinder.clone.moderation.domain.model.ContentType
import java.time.Instant
import java.util.UUID

data class ModerationRequestedEvent(
    val schemaVersion: Int = 1,
    val messageId: UUID,
    val correlationId: String,
    val occurredAt: Instant,
    val contentId: String,
    val contentType: ContentType,
    val text: String? = null,
    val imageUrls: List<String> = emptyList(),
    val locale: String? = null,
    val country: String? = null,
    val authorId: String? = null,
    val conversationContext: List<ContextMessageEvent> = emptyList()
)

data class ContextMessageEvent(
    val contentId: String,
    val authorId: String,
    val text: String
)

data class ModerationCompletedEvent(
    val schemaVersion: Int = 1,
    val messageId: UUID,
    val correlationId: String,
    val occurredAt: Instant,
    val requestMessageId: UUID?,
    val decisionId: UUID,
    val contentId: String,
    val decision: String,
    val reason: String?,
    val policyVersion: String,
    val reviewTaskId: UUID?,
    val evidence: Map<String, Any?>
)

data class ReviewChangedEvent(
    val schemaVersion: Int = 1,
    val messageId: UUID,
    val correlationId: String,
    val occurredAt: Instant,
    val reviewTaskId: UUID,
    val decisionId: UUID,
    val status: String,
    val resolution: String?,
    val aggregateVersion: Long
)

data class PolicyChangedEvent(
    val schemaVersion: Int = 1,
    val messageId: UUID,
    val correlationId: String,
    val occurredAt: Instant,
    val policyVersion: String,
    val changeType: String,
    val contentType: String?,
    val locale: String?,
    val previousVersion: String?
)
