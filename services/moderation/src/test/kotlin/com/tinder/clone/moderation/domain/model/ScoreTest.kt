package com.tinder.clone.moderation.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class ScoreTest {

    @Test
    fun `Score should accept valid values between 0 and 1`() {
        // Valid boundary values
        val scoreZero = Score(0.0)
        val scoreOne = Score(1.0)
        val scoreMiddle = Score(0.5)

        assertTrue(scoreZero.value == 0.0)
        assertTrue(scoreOne.value == 1.0)
        assertTrue(scoreMiddle.value == 0.5)
    }

    @Test
    fun `Score should throw exception when value is less than 0`() {
        assertThrows<IllegalArgumentException> {
            Score(-0.1)
        }
    }

    @Test
    fun `Score should throw exception when value is greater than 1`() {
        assertThrows<IllegalArgumentException> {
            Score(1.1)
        }
    }

}
