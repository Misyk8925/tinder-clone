package com.tinder.clone.moderation.application.policy

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.policy.CategoryThreshold
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RuntimePolicyRegistryTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-06T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `published policy is immutable and activation changes runtime decision`() {
        val registry = RuntimePolicyRegistry(clock)
        registry.create("v1", null, listOf(scope(review = 0.5, block = 0.9)))
        registry.publish("v1", 0)
        registry.activate("v1", ContentType.MESSAGE, "en")
        val content = ModerationContent("message", ContentType.MESSAGE, "text", locale = "en")

        val result = registry.policyFor(content).decide(content, evidence(0.95))

        assertIs<Decision.Block>(result.decision)
        assertEquals("v1", result.evidence.appliedPolicy?.version)
        val error = assertFailsWith<PolicyLifecycleException> {
            registry.replaceDraft("v1", 1, null, listOf(scope(0.8, 0.99)))
        }
        assertEquals("PUBLISHED_POLICY_IMMUTABLE", error.code)
    }

    @Test
    fun `locale activation wins over content type and global activation`() {
        val registry = RuntimePolicyRegistry(clock)
        listOf("global", "message", "locale").forEachIndexed { index, version ->
            val scope = when (index) {
                0 -> PolicyScopeDefinition(null, null, thresholds(0.1 + index, 0.9))
                1 -> PolicyScopeDefinition(ContentType.MESSAGE, null, thresholds(0.2, 0.9))
                else -> PolicyScopeDefinition(ContentType.MESSAGE, "de-AT", thresholds(0.3, 0.9))
            }
            registry.create(version, null, listOf(scope))
            registry.publish(version, 0)
            registry.activate(version, scope.contentType, scope.locale)
        }

        val selected = registry.policyFor(ModerationContent("id", ContentType.MESSAGE, "text", locale = "de-AT"))

        assertEquals("locale", selected.version)
    }

    private fun scope(review: Double, block: Double) =
        PolicyScopeDefinition(ContentType.MESSAGE, "en", thresholds(review, block))

    private fun thresholds(review: Double, block: Double) = mapOf(
        ModerationCategory.HARASSMENT to CategoryThreshold(review, block)
    )

    private fun evidence(score: Double): ModerationEvidence {
        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() } + mapOf(
            ModerationCategory.HARASSMENT to CategoryScore.supported(score, score >= 0.9)
        )
        return ModerationEvidence(ClassificationResult("test", "model", "v1", categories, score >= 0.9, 1))
    }
}
