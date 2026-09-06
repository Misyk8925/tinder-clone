package com.tinder.clone.moderation.infrastructure.provider

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("moderation.providers.gemini")
data class GeminiProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val model: String = "gemini-3.8-flash",
    val connectTimeout: Duration = Duration.ofMillis(500),
    val readTimeout: Duration = Duration.ofMillis(1500)
)
