package com.tinder.clone.moderation.domain.signals

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.application.service.EvidenceBuilder
import com.tinder.clone.moderation.common.enums.LlmLabel
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.policy.ModerationPolicy
import com.tinder.clone.moderation.domain.policy.moderationPolicies
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationContent
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertIs

class ModerationEvidenceTest {
    @Test
    fun `combining evidence preserves classifier provenance scores and conflicting signals`() {
        val classification = classification(
            ModerationCategory.HARASSMENT to CategoryScore.supported(0.8, flagged = true)
        )
        val first = ApplicationSignal(ApplicationSignalType.URL_COUNT, mapOf("count" to "2"))
        val second = ApplicationSignal(ApplicationSignalType.DUPLICATE, mapOf("contentId" to "original-1"))

        val evidence = EvidenceBuilder().fromClassification(classification, listOf(first))
            .withApplicationSignals(listOf(second))
            .withAdjudication(AdjudicationResult("openai", "gpt-test", LlmLabel.SAFE, 0.91))

        assertEquals("openai", evidence.classifierResult.provider)
        assertEquals("omni-moderation-latest", evidence.classifierResult.model)
        assertEquals(0.8, evidence.category(ModerationCategory.HARASSMENT).score?.value)
        assertEquals(listOf(first, second), evidence.applicationSignals)
        assertEquals(LlmLabel.SAFE, evidence.adjudication?.label)
        assertEquals(true, evidence.category(ModerationCategory.HARASSMENT).flagged)
        assertEquals(LlmLabel.SAFE, evidence.adjudication?.label)
    }

    @Test
    fun `unsupported category remains unsupported and is not interpreted as safe`() {
        val evidence = EvidenceBuilder().fromClassification(classification())
        val category = evidence.category(ModerationCategory.UNDERAGE_RISK)

        assertFalse(category.supported)
        assertNull(category.score)
    }

    @Test
    fun `json round trip does not change evidence`() {
        val evidence = EvidenceBuilder().fromClassification(
            classification(ModerationCategory.SPAM to CategoryScore.supported(0.97, flagged = true)),
            listOf(ApplicationSignal(ApplicationSignalType.VERIFIED_SCAM_INDICATOR, mapOf("indicatorId" to "scam-7")))
        ).withRuleHit(RuleHit("spam", Reason.SPAM, 1.0))
            .withAppliedPolicy(AppliedPolicy("v1", ContentType.MESSAGE, "de-AT"))
        val mapper = jacksonObjectMapper()

        val restored = mapper.readValue<ModerationEvidence>(mapper.writeValueAsString(evidence))

        assertEquals(evidence, restored)
    }

    @Test
    fun `policy reads evidence and records the matching rule`() {
        val evidence = EvidenceBuilder().fromClassification(
            classification(ModerationCategory.SPAM to CategoryScore.supported(0.97, flagged = true))
        )

        val result = ModerationPolicy(
            moderationPolicies {
                version("v1") {
                    global { category(ModerationCategory.SPAM, review = 0.5, block = 0.9) }
                }
            },
            "v1"
        ).decide(ModerationContent("1", ContentType.MESSAGE, "text"), evidence)

        assertIs<Decision.Block>(result.decision)
        assertEquals("threshold:SPAM", result.evidence.ruleHits.single().ruleId)
        assertEquals(Reason.CATEGORY_BLOCK, result.evidence.ruleHits.single().reason)
    }

    private fun classification(
        vararg overrides: Pair<ModerationCategory, CategoryScore>
    ): ClassificationResult {
        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() } + overrides
        return ClassificationResult(
            provider = "openai",
            model = "omni-moderation-latest",
            modelSnapshot = "omni-moderation-2024-09-26",
            categories = categories,
            flagged = categories.values.any { it.flagged },
            latencyMs = 12
        )
    }
}
