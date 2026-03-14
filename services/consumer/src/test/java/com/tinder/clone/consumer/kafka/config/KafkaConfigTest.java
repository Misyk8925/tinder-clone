package com.tinder.clone.consumer.kafka.config;

import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    @Test
    void matchEventProducerFactoryUsesJsonSerializer() {
        KafkaConfig kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");

        DefaultKafkaProducerFactory<String, MatchCreateEvent> producerFactory =
                (DefaultKafkaProducerFactory<String, MatchCreateEvent>) kafkaConfig.matchEventProducerFactory();
        Map<String, Object> configuration = producerFactory.getConfigurationProperties();

        assertThat(configuration)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(JacksonJsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    }
}

