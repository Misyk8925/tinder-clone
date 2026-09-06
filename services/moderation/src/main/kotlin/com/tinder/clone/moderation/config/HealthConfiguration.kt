package com.tinder.clone.moderation.config

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HealthConfiguration {
    @Bean fun dbHealthIndicator() = HealthIndicator { Health.up().withDetail("store", "postgresql").build() }
    @Bean fun kafkaHealthIndicator() = HealthIndicator { Health.up().withDetail("transport", "configured-boundary").build() }
}
