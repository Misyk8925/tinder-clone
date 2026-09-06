package com.tinder.clone.moderation.infrastructure.provider

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("moderation.providers.openai")
data class OpenAiModerationProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com",
    val model: String = "omni-moderation-latest",
    val connectTimeout: Duration = Duration.ofMillis(500),
    val readTimeout: Duration = Duration.ofMillis(1500)
)
