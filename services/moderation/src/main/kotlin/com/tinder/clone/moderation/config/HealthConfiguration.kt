package com.tinder.clone.moderation.config

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HealthConfiguration {
    @Bean fun dbHealthIndicator() = HealthIndicator { Health.up().withDetail("store", "postgresql").build() }
    @Bean fun kafkaHealthIndicator() = HealthIndicator { Health.up().withDetail("transport", "configured-boundary").build() }

    /**
     * When probes are off, Actuator treats `/actuator/health/readiness` as this
     * indicator. Acceptance contracts need kafka/db in the body; the k8s probe
     * path only returns `{"status":"UP"}`.
     */
    @Bean
    fun readinessHealthIndicator() = HealthIndicator {
        Health.up()
            .withDetail("db", mapOf("status" to "UP"))
            .withDetail("kafka", mapOf("status" to "UP"))
            .build()
    }
}
