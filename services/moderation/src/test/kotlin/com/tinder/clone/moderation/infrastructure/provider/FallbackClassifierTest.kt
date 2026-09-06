package com.tinder.clone.moderation.infrastructure.provider

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationCategory
import com.tinder.clone.moderation.domain.model.ModerationContent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FallbackClassifierTest {
    @Test
    fun `blank provider keys still return supported zero scores for clean text`() {
        val result = FallbackClassifier().classify(
            ModerationContent("bio-1", ContentType.PROFILE_DESCRIPTION, "hello")
        )

        assertEquals("fallback", result.provider)
        assertEquals("keyword", result.model)
        assertFalse(result.flagged)
        assertTrue(result.categories[ModerationCategory.HARASSMENT]!!.supported)
        assertEquals(0.0, result.categories[ModerationCategory.HARASSMENT]!!.score?.value)
        assertFalse(result.categories[ModerationCategory.SPAM]!!.supported)
    }

    @Test
    fun `hate copy on a profile is flagged for the hate category`() {
        val result = FallbackClassifier().classify(
            ModerationContent(
                "bio-hate",
                ContentType.PROFILE_DESCRIPTION,
                "I hate all outsiders and they should die"
            )
        )

        assertTrue(result.flagged)
        assertTrue(result.categories[ModerationCategory.HATE]!!.flagged)
        assertEquals(0.95, result.categories[ModerationCategory.HATE]!!.score?.value)
    }

    @Test
    fun `a threatening chat message is flagged for harassment`() {
        val result = FallbackClassifier().classify(
            ModerationContent("msg-1", ContentType.MESSAGE, "kill yourself")
        )

        assertTrue(result.flagged)
        assertTrue(result.categories[ModerationCategory.HARASSMENT]!!.flagged)
    }
}
