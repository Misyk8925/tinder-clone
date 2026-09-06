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
    fun `blank provider keys still return supported zero scores for OpenAI categories`() {
        val result = FallbackClassifier().classify(
            ModerationContent("bio-1", ContentType.PROFILE_DESCRIPTION, "hello")
        )

        assertEquals("fallback", result.provider)
        assertFalse(result.flagged)
        assertTrue(result.categories[ModerationCategory.HARASSMENT]!!.supported)
        assertEquals(0.0, result.categories[ModerationCategory.HARASSMENT]!!.score?.value)
        assertFalse(result.categories[ModerationCategory.SPAM]!!.supported)
    }
}
