package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FallbackClassifierTest {
    @Test
    fun `blank provider key never fabricates safe scores for clean text`() {
        val result = FallbackClassifier().classify(
            ModerationContent("bio-1", ContentType.PROFILE_DESCRIPTION, "hello")
        )

        assertEquals("fallback", result.provider)
        assertEquals("unsupported", result.model)
        assertFalse(result.flagged)
        ModerationCategory.entries.forEach { category ->
            assertFalse(result.categories.getValue(category).supported)
        }
    }

    @Test
    fun `blank provider key never converts keyword hits into classifier decisions`() {
        val result = FallbackClassifier().classify(
            ModerationContent(
                "bio-hate",
                ContentType.PROFILE_DESCRIPTION,
                "I hate all outsiders and they should die"
            )
        )

        assertFalse(result.flagged)
        ModerationCategory.entries.forEach { category ->
            assertFalse(result.categories.getValue(category).supported)
            assertFalse(result.categories.getValue(category).flagged)
        }
    }
}
