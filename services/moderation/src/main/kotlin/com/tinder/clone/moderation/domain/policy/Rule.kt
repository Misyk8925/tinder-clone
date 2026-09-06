package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.signals.ModerationEvidence

interface Rule {
    val id: String get() = this::class.simpleName ?: "anonymous-rule"
    fun evaluate(evidence: ModerationEvidence): Decision?
}
