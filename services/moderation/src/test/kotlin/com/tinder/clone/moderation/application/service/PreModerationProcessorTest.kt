package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.domain.model.ContentType
import com.tinder.clone.moderation.domain.model.ModerationContent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PreModerationProcessorTest {
    @Test fun `normalizes unicode and preserves contextual text`() {
        val content = ModerationContent("1", ContentType.MESSAGE, "ｅcho from a film quote")
        val result = PreModerationProcessor().process(content)
        assertEquals("echo from a film quote", assertIs<PreprocessingOutcome.Ready>(result).content.text)
    }

    @Test fun `rejects oversized payload technically`() {
        val content = ModerationContent("1", ContentType.MESSAGE, "12345")
        assertIs<PreprocessingOutcome.Invalid>(PreModerationProcessor(4).process(content))
    }

    @Test fun `throttles when the sliding window is already full`() {
        val limiter = SlidingWindowTrafficLimiter(1)
        val processor = PreModerationProcessor(trafficLimiter = limiter)
        val first = ModerationContent("1", ContentType.MESSAGE, "first")
        val second = ModerationContent("2", ContentType.MESSAGE, "second")
        assertIs<PreprocessingOutcome.Ready>(processor.process(first))
        assertIs<PreprocessingOutcome.Throttled>(processor.process(second))
    }
}
