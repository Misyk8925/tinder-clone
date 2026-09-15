package com.tinder.clone.moderation.config

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import java.util.concurrent.TimeUnit

@Configuration
class HealthConfiguration {
    @Bean
    fun dbHealthIndicator(
        jdbcProvider: ObjectProvider<JdbcTemplate>,
        @Value("\${moderation.persistence.mode:jdbc}") mode: String
    ) = HealthIndicator {
        if (mode == "memory") {
            Health.up().withDetail("store", "memory").build()
        } else {
            try {
                jdbcProvider.getObject().queryForObject("SELECT 1", Int::class.java)
                Health.up().withDetail("store", "postgresql").build()
            } catch (error: Exception) {
                Health.down(error).withDetail("store", "postgresql").build()
            }
        }
    }

    @Bean
    fun kafkaHealthIndicator(
        kafka: ModerationKafkaProperties,
        @Value("\${spring.kafka.bootstrap-servers:localhost:9092}") bootstrapServers: String
    ) = HealthIndicator {
        if (!kafka.enabled) {
            Health.up().withDetail("transport", "disabled").withDetail("enabled", false).build()
        } else {
            try {
                AdminClient.create(
                    mapOf(
                        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                        AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG to 1000,
                        AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG to 1000
                    )
                ).use { admin ->
                    admin.describeCluster().nodes().get(1, TimeUnit.SECONDS)
                }
                Health.up().withDetail("transport", "kafka").withDetail("enabled", true).build()
            } catch (error: Exception) {
                Health.down(error).withDetail("transport", "kafka").withDetail("enabled", true).build()
            }
        }
    }
}
