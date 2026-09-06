package com.tinder.clone.moderation.application.service

import com.tinder.clone.moderation.domain.model.ModerationContent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque

class SlidingWindowTrafficLimiter(
    private val requestsPerMinute: Int,
    private val clock: Clock = Clock.systemUTC()
) : TrafficLimiter {
    private val hits = ArrayDeque<Instant>()

    @Synchronized
    override fun retryAfter(content: ModerationContent): Duration? {
        if (requestsPerMinute <= 0) return null
        val now = Instant.now(clock)
        val windowStart = now.minusSeconds(60)
        while (hits.isNotEmpty() && !hits.first().isAfter(windowStart)) {
            hits.removeFirst()
        }
        if (hits.size >= requestsPerMinute) {
            val retry = Duration.between(now, hits.first().plusSeconds(60))
            return if (retry.isZero || retry.isNegative) Duration.ofSeconds(1) else retry
        }
        hits.addLast(now)
        return null
    }
}
