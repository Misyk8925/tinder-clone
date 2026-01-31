package com.tinder.profiles.util;

import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test Kafka consumer configuration for capturing events during integration tests
 */
@TestConfiguration
public class TestKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Consumer factory for ProfileCreateEvent with proper JSON deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileCreateEvent> testProfileCreateEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        // Use ErrorHandlingDeserializer as wrapper
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Delegate to actual deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JSON deserializer specific config
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.profiles.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileCreateEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Consumer factory for ProfileUpdatedEvent with proper JSON deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileUpdatedEvent> testProfileUpdatedEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.profiles.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileUpdatedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Consumer factory for ProfileDeleteEvent with proper JSON deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileDeleteEvent> testProfileDeleteEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.profiles.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileDeleteEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory for ProfileCreateEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> testProfileCreateKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(testProfileCreateEventConsumerFactory());
        return factory;
    }

    /**
     * Listener container factory for ProfileUpdatedEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileUpdatedEvent> testProfileUpdatedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProfileUpdatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(testProfileUpdatedEventConsumerFactory());
        return factory;
    }

    /**
     * Listener container factory for ProfileDeleteEvent
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> testProfileDeleteKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(testProfileDeleteEventConsumerFactory());
        return factory;
    }

    /**
     * Collector component that captures all Kafka events during tests
     */
    @Component
    @Slf4j
    @Getter
    public static class TestKafkaEventCollector {

        private final List<ProfileCreateEvent> profileCreatedEvents =
            Collections.synchronizedList(new ArrayList<>());
        private final List<ProfileUpdatedEvent> profileUpdatedEvents =
            Collections.synchronizedList(new ArrayList<>());
        private final List<ProfileDeleteEvent> profileDeletedEvents =
            Collections.synchronizedList(new ArrayList<>());

        @KafkaListener(
            topics = "${kafka.topics.profile-events.created}",
            groupId = "test-consumer-group",
            containerFactory = "testProfileCreateKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileCreated(ProfileCreateEvent event) {
            log.info("Test consumer received ProfileCreateEvent: eventId={}, profileId={}",
                event.getEventId(), event.getProfileId());
            profileCreatedEvents.add(event);
        }

        @KafkaListener(
            topics = "${kafka.topics.profile-events.updated}",
            groupId = "test-consumer-group",
            containerFactory = "testProfileUpdatedKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileUpdated(ProfileUpdatedEvent event) {
            log.info("Test consumer received ProfileUpdatedEvent: eventId={}, profileId={}",
                event.getEventId(), event.getProfileId());
            profileUpdatedEvents.add(event);
        }

        @KafkaListener(
            topics = "${kafka.topics.profile-events.deleted}",
            groupId = "test-consumer-group",
            containerFactory = "testProfileDeleteKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileDeleted(ProfileDeleteEvent event) {
            log.info("Test consumer received ProfileDeleteEvent: eventId={}, profileId={}",
                event.getEventId(), event.getProfileId());
            profileDeletedEvents.add(event);
        }

        /**
         * Reset all collected events (call before each test)
         */
        public void reset() {
            profileCreatedEvents.clear();
            profileUpdatedEvents.clear();
            profileDeletedEvents.clear();
            log.info("Test Kafka event collector reset");
        }

        /**
         * Get count of all collected events
         */
        public int getTotalEventsCount() {
            return profileCreatedEvents.size() +
                   profileUpdatedEvents.size() +
                   profileDeletedEvents.size();
        }
    }
}
