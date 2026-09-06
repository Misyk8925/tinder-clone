package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.signals.ApplicationSignal
import com.tinder.clone.moderation.domain.signals.ApplicationSignalType
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ModerationPolicyEvidenceTest {
    @Test fun `verified scam rule consumes application signal`() {
        val withSignal = evidence().copy(applicationSignals = listOf(
            ApplicationSignal(ApplicationSignalType.VERIFIED_SCAM_INDICATOR, mapOf("indicatorId" to "7"))
        ))
        assertIs<Decision.Block>(VerifiedScamIndicatorRule().evaluate(withSignal))
        assertNull(VerifiedScamIndicatorRule().evaluate(evidence()))
    }

    @Test fun `policy preserves application and category threshold rule hits`() {
        val result = ModerationPolicy(
            moderationPolicies {
                version("v1") {
                    global { category(ModerationCategory.SPAM, review = 0.5, block = 0.9) }
                }
            },
            "v1",
            listOf(VerifiedScamIndicatorRule())
        ).decide(
            ModerationContent("1", ContentType.MESSAGE, "text"),
            evidence(ModerationCategory.SPAM, 0.99, true).copy(applicationSignals = listOf(
                ApplicationSignal(ApplicationSignalType.VERIFIED_SCAM_INDICATOR)
            ))
        )
        assertIs<Decision.Block>(result.decision)
        assertEquals(
            listOf("verified-scam-indicator", "threshold:SPAM"),
            result.evidence.ruleHits.map { it.ruleId }
        )
    }

    private fun evidence(
        category: ModerationCategory? = null,
        score: Double = 0.0,
        flagged: Boolean = false
    ): ModerationEvidence {
        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }.toMutableMap()
        if (category != null) categories[category] = CategoryScore.supported(score, flagged)
        return ModerationEvidence(
            ClassificationResult("test", "classifier", "v1", categories, categories.values.any { it.flagged }, 1)
        )
    }
}
