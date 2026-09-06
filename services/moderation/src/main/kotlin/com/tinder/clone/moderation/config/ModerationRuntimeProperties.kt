package com.tinder.clone.moderation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("moderation.runtime")
data class ModerationRuntimeProperties(
    val activePolicyVersion: String = "unconfigured"
)
