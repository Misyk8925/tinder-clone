package com.tinder.deck.kafka.config;

import com.tinder.deck.kafka.dto.ProfileEvent;
import com.tinder.deck.kafka.dto.SwipeCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for testing
 * This allows integration tests to send ProfileEvent and SwipeCreatedEvent messages
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Producer factory for ProfileEvent serialization
     */
    @Bean
    public ProducerFactory<String, ProfileEvent> profileEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate for sending ProfileEvent messages
     */
    @Bean
    public KafkaTemplate<String, ProfileEvent> kafkaTemplate() {
        return new KafkaTemplate<>(profileEventProducerFactory());
    }

    /**
     * Producer factory for SwipeCreatedEvent serialization
     */
    @Bean
    public ProducerFactory<String, SwipeCreatedEvent> swipeEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate for sending SwipeCreatedEvent messages
     */
    @Bean
    public KafkaTemplate<String, SwipeCreatedEvent> swipeKafkaTemplate() {
        return new KafkaTemplate<>(swipeEventProducerFactory());
    }
}
