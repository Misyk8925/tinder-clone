package com.tinder.clone.moderation.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.boot.health.contributor.Status
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import kotlin.test.assertEquals

class HealthConfigurationTest {
    private val configuration = HealthConfiguration()

    @Test
    fun `jdbc readiness is down when PostgreSQL cannot be reached`() {
        val beans = DefaultListableBeanFactory()
        beans.registerSingleton(
            "jdbcTemplate",
            JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/unavailable"))
        )
        val indicator = configuration.dbHealthIndicator(
            beans.getBeanProvider(JdbcTemplate::class.java),
            "jdbc"
        )

        assertEquals(Status.DOWN, indicator.health().status)
    }

    @Test
    fun `Kafka readiness is down when enabled broker cannot be reached`() {
        val indicator = configuration.kafkaHealthIndicator(
            ModerationKafkaProperties(enabled = true),
            "127.0.0.1:1"
        )

        assertEquals(Status.DOWN, indicator.health().status)
    }
}
