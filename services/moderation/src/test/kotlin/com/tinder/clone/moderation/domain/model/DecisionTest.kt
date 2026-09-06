package com.tinder.clone.moderation.domain.model

import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DecisionTest {

    @Test
    fun `hold keeps technical policy reason distinct from content block`() {
        val hold = Decision.Hold(Reason.POLICY_NOT_CONFIGURED)

        assertEquals(Reason.POLICY_NOT_CONFIGURED, hold.reason)
        assertIs<Decision.Hold>(hold)
    }

    @Test
    fun `Allow decision should be created as singleton`() {
        val allow1 = Decision.Allow
        val allow2 = Decision.Allow

        assertEquals(allow1, allow2)
        assertIs<Decision.Allow>(allow1)
    }

    @Test
    fun `Block decision should contain reason and confidence`() {
        val reason = Reason.SPAM
        val confidence = 0.95

        val block = Decision.Block(reason, confidence)

        assertEquals(reason, block.reason)
        assertEquals(confidence, block.confidence)
        assertIs<Decision.Block>(block)
    }

    @Test
    fun `Block decisions with same reason and confidence should be equal`() {
        val reason = Reason.TOXICITY_HIGH
        val confidence = 0.9

        val block1 = Decision.Block(reason, confidence)
        val block2 = Decision.Block(reason, confidence)

        assertEquals(block1, block2)
    }

    @Test
    fun `Block decisions with different reasons should not be equal`() {
        val confidence = 0.9

        val block1 = Decision.Block(Reason.SPAM, confidence)
        val block2 = Decision.Block(Reason.TOXICITY_HIGH, confidence)

        assertEquals(false, block1 == block2)
    }

    @Test
    fun `Flag decision should contain reason, confidence and date`() {
        val reason = Reason.TOXICITY_MEDIUM
        val confidence = 0.7
        val date = Date()

        val flag = Decision.Flag(reason, confidence, date)

        assertEquals(reason, flag.reason)
        assertEquals(confidence, flag.confidence)
        assertEquals(date, flag.date)
        assertIs<Decision.Flag>(flag)
    }

    @Test
    fun `Flag decisions with same parameters should be equal`() {
        val reason = Reason.LLM_UNCERTAIN
        val confidence = 0.65
        val date = Date()

        val flag1 = Decision.Flag(reason, confidence, date)
        val flag2 = Decision.Flag(reason, confidence, date)

        assertEquals(flag1, flag2)
    }

    @Test
    fun `Different decision types should have different behavior`() {
        val allow = Decision.Allow
        val block = Decision.Block(Reason.SPAM, 1.0)
        val flag = Decision.Flag(Reason.TOXICITY_MEDIUM, 0.7, Date())

        assertIs<Decision.Allow>(allow)
        assertIs<Decision.Block>(block)
        assertIs<Decision.Flag>(flag)
    }
}
