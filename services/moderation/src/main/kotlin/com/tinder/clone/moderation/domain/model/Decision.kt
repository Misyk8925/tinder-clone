package com.tinder.clone.moderation.domain.model

import java.util.Date


sealed class Decision {

    data object Allow : Decision()

    data class Hold(val reason: Reason) : Decision()

    data class Block(
        val reason: Reason,
        val confidence: Double
    ) : Decision()

    data class Flag(
        val reason: Reason,
        val confidence: Double,
        val date: Date
    ) : Decision()
}
