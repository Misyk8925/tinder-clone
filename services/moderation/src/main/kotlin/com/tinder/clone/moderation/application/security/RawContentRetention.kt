package com.tinder.clone.moderation.application.security

import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

class RawContentRetention(
    private val expire: (java.time.Instant) -> Int,
    private val clock: Clock
) {
    fun purgeExpired(): Int = expire(clock.instant())
}

@Component
class RawContentRetentionJob(
    store: ModerationDecisionStore,
    clock: Clock
) {
    private val retention = RawContentRetention(store::purgeExpiredRawContent, clock)

    @Scheduled(cron = "\${moderation.retention.cron:0 15 3 * * *}")
    fun purgeExpired() {
        retention.purgeExpired()
    }
}
