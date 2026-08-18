package com.tinder.deck.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaDeliveryConfigurationTest {

    private KafkaConsumerConfig consumerConfig;

    @BeforeEach
    void setUp() {
        consumerConfig = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(consumerConfig, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(consumerConfig, "groupId", "deck-test");
        ReflectionTestUtils.setField(consumerConfig, "profileUpdatedTopic", "profile.updated");
        ReflectionTestUtils.setField(consumerConfig, "swipeSavedTopic", "swipe-saved");
        ReflectionTestUtils.setField(consumerConfig, "profileDeletedTopic", "profile.deleted");
    }

    @Test
    void criticalConsumersCommitOnlyAfterProcessingAndReadCommittedData() {
        DefaultKafkaConsumerFactory<?, ?> factory =
                (DefaultKafkaConsumerFactory<?, ?>) consumerConfig.swipeEventConsumerFactory();

        assertThat(factory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
                .containsEntry(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    }

    @Test
    void deadLetterTopicsUseNormalizedNames() {
        assertThat(consumerConfig.profileUpdatedDeadLetterTopic().name()).isEqualTo("profile.updated.dlt");
        assertThat(consumerConfig.swipeSavedDeadLetterTopic().name()).isEqualTo("swipe-saved.dlt");
        assertThat(consumerConfig.profileDeletedDeadLetterTopic().name()).isEqualTo("profile.deleted.dlt");
    }

    @Test
    void allDeckProducersUseDurableIdempotentSettings() {
        KafkaProducerConfig producerConfig = new KafkaProducerConfig();
        ReflectionTestUtils.setField(producerConfig, "bootstrapServers", "localhost:9092");

        assertDurable(((DefaultKafkaProducerFactory<?, ?>) producerConfig.deckBuiltProducerFactory())
                .getConfigurationProperties());
        assertDurable(((DefaultKafkaProducerFactory<?, ?>) producerConfig.deadLetterProducerFactory())
                .getConfigurationProperties());
    }

    private void assertDurable(Map<String, Object> properties) {
        assertThat(properties)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    }
}
