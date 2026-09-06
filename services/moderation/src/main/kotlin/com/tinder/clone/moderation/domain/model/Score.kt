package com.tinder.clone.moderation.domain.model

@JvmInline
value class Score(val value: Double) {
    init {
        require(value in 0.0..1.0)
    }
}
