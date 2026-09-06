package com.tinder.clone.moderation.application.ports

import com.tinder.clone.moderation.infrastructure.http.ModerationRequestDto
import com.tinder.clone.moderation.infrastructure.http.ModerationResponseDto
import java.util.UUID

interface ModerationOutboxPort {
    fun enqueueCompleted(request: ModerationRequestDto, response: ModerationResponseDto, requestMessageId: UUID?)
}

class NoOpModerationOutbox : ModerationOutboxPort {
    override fun enqueueCompleted(
        request: ModerationRequestDto,
        response: ModerationResponseDto,
        requestMessageId: UUID?
    ) = Unit
}
