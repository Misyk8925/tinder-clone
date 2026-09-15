package com.tinder.clone.moderation.application.ports

import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import com.tinder.clone.moderation.domain.model.ContentType
import java.util.UUID

interface ModerationOutboxPort {
    fun enqueueCompleted(request: ModerationRequestDto, response: ModerationResponseDto, requestMessageId: UUID?)
    fun enqueueReviewChanged(
        reviewTaskId: UUID,
        decisionId: UUID,
        status: String,
        resolution: String?,
        aggregateVersion: Long
    )
    fun enqueuePolicyChanged(
        policyVersion: String,
        changeType: String,
        contentType: ContentType?,
        locale: String?,
        previousVersion: String?
    )
}

class NoOpModerationOutbox : ModerationOutboxPort {
    override fun enqueueCompleted(
        request: ModerationRequestDto,
        response: ModerationResponseDto,
        requestMessageId: UUID?
    ) = Unit

    override fun enqueueReviewChanged(
        reviewTaskId: UUID,
        decisionId: UUID,
        status: String,
        resolution: String?,
        aggregateVersion: Long
    ) = Unit

    override fun enqueuePolicyChanged(
        policyVersion: String,
        changeType: String,
        contentType: ContentType?,
        locale: String?,
        previousVersion: String?
    ) = Unit
}
