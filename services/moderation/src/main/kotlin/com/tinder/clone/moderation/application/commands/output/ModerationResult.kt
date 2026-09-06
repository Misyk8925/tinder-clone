package com.tinder.clone.moderation.application.commands.output

import com.tinder.clone.moderation.application.service.ValidationReason
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import java.time.Duration

sealed interface ModerationResult {
    data class Evaluated(val decision: Decision, val evidence: ModerationEvidence) : ModerationResult
    data class Invalid(val reason: ValidationReason) : ModerationResult
    data class Throttled(val retryAfter: Duration) : ModerationResult {
        init { require(!retryAfter.isNegative && !retryAfter.isZero) }
    }
}
