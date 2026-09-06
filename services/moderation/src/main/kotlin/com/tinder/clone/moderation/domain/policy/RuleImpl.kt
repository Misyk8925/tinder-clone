package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.domain.signals.ApplicationSignalType

class VerifiedScamIndicatorRule : Rule {
    override val id = "verified-scam-indicator"

    override fun evaluate(evidence: ModerationEvidence): Decision? =
        if (evidence.hasApplicationSignal(ApplicationSignalType.VERIFIED_SCAM_INDICATOR)) {
            Decision.Block(Reason.SPAM, 1.0)
        } else null
}
