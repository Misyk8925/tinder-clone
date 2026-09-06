package com.tinder.clone.moderation.application.commands.output

import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.Score
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassificationResultTest {

    @Test
    fun `given classifier output when result is created then provenance and category scores are retained`() {
        val result = ClassificationResult(
            provider = "openai",
            model = "omni-moderation-latest",
            modelSnapshot = "omni-moderation-2024-09-26",
            categories = completeCategories(
                mapOf(
                    ModerationCategory.HARASSMENT_THREATENING to CategoryScore(
                        score = Score(0.91),
                        flagged = true,
                        supported = true
                    )
                )
            ),
            flagged = true,
            latencyMs = 42
        )

        assertEquals("openai", result.provider)
        assertEquals("omni-moderation-latest", result.model)
        assertEquals("omni-moderation-2024-09-26", result.modelSnapshot)
        assertEquals(true, result.flagged)
        assertEquals(42, result.latencyMs)
        assertEquals(Score(0.91), result.categories.getValue(ModerationCategory.HARASSMENT_THREATENING).score)
    }

    @Test
    fun `given unsupported category when result is created then it cannot pretend to be safe`() {
        val score = CategoryScore.unsupported()

        assertFalse(score.supported)
        assertEquals(null, score.score)
        assertThrows<IllegalArgumentException> {
            CategoryScore(score = null, flagged = true, supported = false)
        }
    }

    @Test
    fun `given supported category when score is created then score and flag are retained`() {
        val score = CategoryScore.supported(score = 0.73, flagged = true)

        assertTrue(score.supported)
        assertTrue(score.flagged)
        assertEquals(Score(0.73), score.score)
    }

    @Test
    fun `category support and score presence must agree`() {
        assertThrows<IllegalArgumentException> {
            CategoryScore(score = null, flagged = false, supported = true)
        }
        assertThrows<IllegalArgumentException> {
            CategoryScore(score = Score(0.0), flagged = false, supported = false)
        }
    }

    @Test
    fun `given optional snapshot when result is created then rolling model is accepted`() {
        val result = validResult(modelSnapshot = null)

        assertNull(result.modelSnapshot)
    }

    @Test
    fun `given blank classifier provenance when result is created then validation fails`() {
        assertThrows<IllegalArgumentException> { validResult(provider = " ") }
        assertThrows<IllegalArgumentException> { validResult(model = " ") }
        assertThrows<IllegalArgumentException> { validResult(modelSnapshot = " ") }
    }

    @Test
    fun `given incomplete categories when result is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            validResult(categories = emptyMap())
        }
    }

    @Test
    fun `given aggregate flag different from category flags when result is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            ClassificationResult(
                provider = "test-provider",
                model = "test-model",
                modelSnapshot = "v1",
                categories = completeCategories(),
                flagged = true,
                latencyMs = 1
            )
        }
    }

    @Test
    fun `given negative latency when result is created then validation fails`() {
        assertThrows<IllegalArgumentException> {
            validResult(latencyMs = -1)
        }
    }

    @Test
    fun `moderation categories expose the complete classifier vocabulary`() {
        assertEquals(
            setOf(
                ModerationCategory.SPAM,
                ModerationCategory.SCAM,
                ModerationCategory.HARASSMENT,
                ModerationCategory.HARASSMENT_THREATENING,
                ModerationCategory.HATE,
                ModerationCategory.HATE_THREATENING,
                ModerationCategory.SEXUAL_CONTENT,
                ModerationCategory.SEXUAL_MINORS,
                ModerationCategory.SELF_HARM,
                ModerationCategory.VIOLENCE,
                ModerationCategory.OFF_PLATFORM_CONTACT,
                ModerationCategory.IMPERSONATION,
                ModerationCategory.UNDERAGE_RISK
            ),
            ModerationCategory.entries.toSet()
        )
    }

    private fun validResult(
        provider: String = "test-provider",
        model: String = "test-model",
        modelSnapshot: String? = "v1",
        categories: Map<ModerationCategory, CategoryScore> = completeCategories(
            mapOf(ModerationCategory.HARASSMENT to CategoryScore.supported(0.1, flagged = false))
        ),
        latencyMs: Long = 1
    ): ClassificationResult = ClassificationResult(
        provider = provider,
        model = model,
        modelSnapshot = modelSnapshot,
        categories = categories,
        flagged = false,
        latencyMs = latencyMs
    )

    private fun completeCategories(
        overrides: Map<ModerationCategory, CategoryScore> = emptyMap()
    ): Map<ModerationCategory, CategoryScore> =
        ModerationCategory.entries.associateWith { CategoryScore.unsupported() } + overrides
}
