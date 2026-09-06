package com.tinder.clone.moderation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("moderation.traffic")
data class ModerationTrafficProperties(
    val requestsPerMinute: Int = 0
)
