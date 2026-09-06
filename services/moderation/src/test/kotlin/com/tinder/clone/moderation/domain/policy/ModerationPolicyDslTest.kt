package com.tinder.clone.moderation.domain.policy

import com.tinder.clone.moderation.application.commands.output.CategoryScore
import com.tinder.clone.moderation.application.commands.output.ClassificationResult
import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.Decision
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import com.tinder.clone.moderation.domain.model.Reason
import com.tinder.clone.moderation.domain.signals.ModerationEvidence
import com.tinder.clone.moderation.domain.signals.AdjudicationResult
import com.tinder.clone.moderation.common.enums.LlmLabel
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ModerationPolicyDslTest {
    @Test
    fun `scores immediately below at and above every threshold have deterministic boundaries`() {
        val policy = policy {
            global { category(ModerationCategory.HARASSMENT, review = 0.5, block = 0.9) }
        }
        val cases = listOf(
            0.499999 to Decision.Allow::class,
            0.5 to Decision.Flag::class,
            0.500001 to Decision.Flag::class,
            0.899999 to Decision.Flag::class,
            0.9 to Decision.Block::class,
            0.900001 to Decision.Block::class
        )

        cases.forEach { (score, expectedType) ->
            val actual = policy.decide(content(ContentType.MESSAGE), evidence(ModerationCategory.HARASSMENT, score)).decision
            assertEquals(expectedType, actual::class, "Unexpected decision at score=$score")
        }
    }

    @Test
    fun `message and profile description can use different thresholds`() {
        val policy = policy {
            contentType(ContentType.MESSAGE) {
                category(ModerationCategory.HARASSMENT, review = 0.5, block = 0.9)
            }
            contentType(ContentType.PROFILE_DESCRIPTION) {
                category(ModerationCategory.HARASSMENT, review = 0.8, block = 0.95)
            }
        }

        assertIs<Decision.Flag>(
            policy.decide(content(ContentType.MESSAGE), evidence(ModerationCategory.HARASSMENT, 0.7)).decision
        )
        assertIs<Decision.Allow>(
            policy.decide(content(ContentType.PROFILE_DESCRIPTION), evidence(ModerationCategory.HARASSMENT, 0.7)).decision
        )
    }

    @Test
    fun `locale scope wins over content type and global scopes`() {
        val policy = policy {
            global { category(ModerationCategory.HARASSMENT, review = 0.2, block = 0.3) }
            contentType(ContentType.MESSAGE) {
                category(ModerationCategory.HARASSMENT, review = 0.5, block = 0.8)
            }
            locale(ContentType.MESSAGE, "de-AT") {
                category(ModerationCategory.HARASSMENT, review = 0.7, block = 0.95)
            }
        }
        val evidence = evidence(ModerationCategory.HARASSMENT, 0.6)

        val locale = policy.decide(content(ContentType.MESSAGE, "de-AT"), evidence)
        val contentType = policy.decide(content(ContentType.MESSAGE, "en-AT"), evidence)
        val global = policy.decide(content(ContentType.PHOTO), evidence)

        assertIs<Decision.Allow>(locale.decision)
        assertEquals("de-AT", locale.evidence.appliedPolicy?.locale)
        assertIs<Decision.Flag>(contentType.decision)
        assertEquals(ContentType.MESSAGE, contentType.evidence.appliedPolicy?.contentType)
        assertEquals(null, contentType.evidence.appliedPolicy?.locale)
        assertIs<Decision.Block>(global.decision)
        assertEquals(null, global.evidence.appliedPolicy?.contentType)
    }

    @Test
    fun `missing policy returns hold instead of allow`() {
        val catalog = moderationPolicies {
            version("v1") { contentType(ContentType.MESSAGE) { } }
        }
        val result = ModerationPolicy(catalog, "v1").decide(content(ContentType.PHOTO), evidence())

        val hold = assertIs<Decision.Hold>(result.decision)
        assertEquals(Reason.POLICY_NOT_CONFIGURED, hold.reason)
        assertEquals("v1", result.evidence.appliedPolicy?.version)
    }

    @Test
    fun `unsupported configured category returns hold instead of allow`() {
        val policy = policy {
            global { category(ModerationCategory.UNDERAGE_RISK, review = 0.5, block = 0.9) }
        }

        val result = policy.decide(content(ContentType.MESSAGE), evidence())

        val hold = assertIs<Decision.Hold>(result.decision)
        assertEquals(Reason.CLASSIFIER_CATEGORY_UNSUPPORTED, hold.reason)
        assertEquals(ModerationCategory.UNDERAGE_RISK, result.evidence.ruleHits.single().category)
        assertEquals(null, result.evidence.ruleHits.single().confidence)
    }

    @Test
    fun `same immutable policy version produces the same result`() {
        val policy = policy {
            global { category(ModerationCategory.VIOLENCE, review = 0.4, block = 0.8) }
        }
        val content = content(ContentType.MESSAGE)
        val evidence = evidence(ModerationCategory.VIOLENCE, 0.81)

        assertEquals(policy.decide(content, evidence), policy.decide(content, evidence))
    }

    @Test
    fun `adjudication boundary is driven by selected policy thresholds`() {
        val policy = policy {
            global { category(ModerationCategory.HARASSMENT, review = 0.5, block = 0.9) }
        }
        val content = content(ContentType.MESSAGE)

        assertEquals(true, policy.requiresAdjudication(content, evidence(ModerationCategory.HARASSMENT, 0.5)))
        assertEquals(false, policy.requiresAdjudication(content, evidence(ModerationCategory.HARASSMENT, 0.9)))
        assertEquals(
            false,
            policy.requiresAdjudication(
                content,
                evidence(ModerationCategory.HARASSMENT, 0.6).withAdjudication(
                    AdjudicationResult("test", "model", LlmLabel.SAFE, 0.9)
                )
            )
        )
    }

    @Test
    fun `published catalog defensively copies thresholds`() {
        val mutableThresholds = mutableMapOf(
            ModerationCategory.SPAM to CategoryThreshold(review = 0.4, block = 0.8)
        )
        val catalog = ModerationPolicyCatalog(
            listOf(ModerationPolicyConfig("v1", null, null, mutableThresholds))
        )
        mutableThresholds[ModerationCategory.SPAM] = CategoryThreshold(review = 0.1, block = 0.2)

        val published = catalog.resolve("v1", ContentType.MESSAGE, null)!!
        assertEquals(0.4, published.thresholds.getValue(ModerationCategory.SPAM).review)
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> {
            (published.thresholds as MutableMap)[ModerationCategory.SPAM] = CategoryThreshold(0.0, 0.0)
        }
    }

    @Test
    fun `dsl rejects invalid threshold order and duplicate scopes`() {
        assertFailsWith<IllegalArgumentException> { CategoryThreshold(review = 0.9, block = 0.5) }
        assertFailsWith<IllegalArgumentException> {
            moderationPolicies {
                version("v1") {
                    global { }
                    global { }
                }
            }
        }
    }

    private fun policy(block: PolicyVersionDsl.() -> Unit): ModerationPolicy = ModerationPolicy(
        catalog = moderationPolicies { version("v1", block) },
        version = "v1",
        clock = Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC)
    )

    private fun content(type: ContentType, locale: String? = null) =
        ModerationContent("content-1", type, "text", locale = locale)

    private fun evidence(
        category: ModerationCategory? = null,
        score: Double = 0.0
    ): ModerationEvidence {
        val categories = ModerationCategory.entries.associateWith { CategoryScore.unsupported() }.toMutableMap()
        if (category != null) categories[category] = CategoryScore.supported(score, flagged = false)
        return ModerationEvidence(
            ClassificationResult("test", "classifier", "v1", categories, false, 1)
        )
    }
}
