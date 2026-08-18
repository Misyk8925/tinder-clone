package com.tinder.match.match.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaDeliveryConfigurationTest {

    private KafkaConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "topic", "match.created");
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "match-test");
        ReflectionTestUtils.setField(config, "concurrency", 1);
    }

    @Test
    void failedMatchesHaveADeclaredDeadLetterTopic() {
        assertThat(config.matchCreatedDeadLetterTopic().name()).isEqualTo("match.created.dlt");
    }

    @Test
    void listenerCommitsOnlyAfterSuccessfulProcessingOrRecovery() {
        DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);
        var factory = config.kafkaListenerContainerFactory(errorHandler);

        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler")).isSameAs(errorHandler);
    }
}
