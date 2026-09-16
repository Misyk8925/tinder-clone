package com.tinder.clone.moderation.config

import com.tinder.clone.moderation.infrastructure.messaging.InMemoryOutboxRecordRepository
import com.tinder.clone.moderation.infrastructure.messaging.JdbcModerationOutbox
import com.tinder.clone.moderation.infrastructure.messaging.OutboxRecordRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock

class OutboxConfigurationTest {
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(Fixture::class.java)
        .withPropertyValues(
            "moderation.persistence.mode=jdbc",
            "moderation.kafka.enabled=false"
        )

    @Test
    fun `JDBC outbox remains active while Kafka publishing is disabled`() {
        runner.run { context ->
            kotlin.test.assertNotNull(context.getBean(JdbcModerationOutbox::class.java))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JdbcModerationOutbox::class)
    class Fixture {
        @Bean fun records(): OutboxRecordRepository = InMemoryOutboxRecordRepository()
        @Bean fun objectMapper() = jacksonObjectMapper()
        @Bean fun kafka() = ModerationKafkaProperties(enabled = false)
        @Bean fun clock(): Clock = Clock.systemUTC()
    }
}
