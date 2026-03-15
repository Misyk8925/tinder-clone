package com.tinder.clone.consumer.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void swipeEventConsumerFactoryUsesCorrectDeserializer() {
        KafkaConfig kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(kafkaConfig, "swipeCreatedTopic", "swipe.created");
        ReflectionTestUtils.setField(kafkaConfig, "profileCreatedTopic", "profile.created");
        ReflectionTestUtils.setField(kafkaConfig, "profileDeletedTopic", "profile.deleted");
        ReflectionTestUtils.setField(kafkaConfig, "matchCreatedTopic", "match.created");
        ReflectionTestUtils.setField(kafkaConfig, "swipeSavedTopic", "swipe.saved");
        ReflectionTestUtils.setField(kafkaConfig, "groupId", "test-group");
        ReflectionTestUtils.setField(kafkaConfig, "concurrency", 1);

        DefaultKafkaConsumerFactory<?, ?> consumerFactory =
                (DefaultKafkaConsumerFactory<?, ?>) kafkaConfig.swipeEventConsumerFactory();
        Map<String, Object> configuration = consumerFactory.getConfigurationProperties();

        assertThat(configuration)
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    }
}

