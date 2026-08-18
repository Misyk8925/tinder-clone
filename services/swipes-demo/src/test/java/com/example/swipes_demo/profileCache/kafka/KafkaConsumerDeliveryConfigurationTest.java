package com.example.swipes_demo.profileCache.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerDeliveryConfigurationTest {

    private KafkaConsumerConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "profileCreatedTopic", "profile.created");
        ReflectionTestUtils.setField(config, "profileDeletedTopic", "profile.deleted");
    }

    @Test
    void profileConsumerUsesReadCommittedAndDisablesAutoCommit() {
        DefaultKafkaConsumerFactory<?, ?> factory =
                (DefaultKafkaConsumerFactory<?, ?>) config.profileCreateEventConsumerFactory();

        assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    }

    @Test
    void listenerUsesRecordCommitAndRetryHandler() {
        var consumerFactory = config.profileCreateEventConsumerFactory();
        DefaultErrorHandler errorHandler = mock(DefaultErrorHandler.class);
        var factory = config.profileCreateEventKafkaListenerContainerFactory(consumerFactory, errorHandler);

        assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.RECORD);
        assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler")).isSameAs(errorHandler);
    }

    @Test
    void failedProfileEventsHaveNormalizedDeadLetterTopics() {
        assertThat(config.profileCreatedDeadLetterTopic().name()).isEqualTo("profile.created.dlt");
        assertThat(config.profileDeletedDeadLetterTopic().name()).isEqualTo("profile.deleted.dlt");
    }
}
