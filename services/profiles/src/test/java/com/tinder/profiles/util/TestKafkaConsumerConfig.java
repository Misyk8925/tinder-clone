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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test Kafka consumer configuration for capturing events during integration tests
 *
 * Key features:
 * - Unique consumer group per test run to avoid conflicts
 * - Manual commit for precise control
 * - Error handling with log-and-skip strategy
 * - No code duplication in consumer factories
 */
@Slf4j
@TestConfiguration
public class TestKafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Generate unique consumer group ID for each test run
     * This prevents offset conflicts between parallel or sequential test runs
     */
    @Bean
    public String testConsumerGroupId() {
        String groupId = "test-consumer-group-" + UUID.randomUUID();
        log.info("Generated unique test consumer group ID: {}", groupId);
        return groupId;
    }

    /**
     * Base consumer properties shared by all event types
     * Eliminates code duplication
     */
    private Map<String, Object> baseConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Use 'earliest' to consume all events from the beginning
        // Test logic will filter by counting increments
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Manual commit for precise control
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Use ErrorHandlingDeserializer as wrapper
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Delegate to actual deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // JSON deserializer specific config
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.profiles.kafka.dto");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return props;
    }

    /**
     * Create consumer factory for specific event type
     * @param valueType The class of the event type to deserialize
     * @param groupId Unique consumer group ID
     */
    private <T> ConsumerFactory<String, T> createConsumerFactory(Class<T> valueType, String groupId) {
        Map<String, Object> props = baseConsumerProps(groupId);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Create listener container factory with error handling
     * @param consumerFactory The consumer factory to use
     */
    private <T> ConcurrentKafkaListenerContainerFactory<String, T> createListenerFactory(
            ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Manual acknowledgment for precise control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // Error handler: log and skip (no retries in tests)
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("Test Kafka consumer error - skipping record: topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset(), exception);
                },
                new FixedBackOff(0L, 0L) // No retries
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public ConsumerFactory<String, ProfileCreateEvent> testProfileCreateEventConsumerFactory(String testConsumerGroupId) {
        return createConsumerFactory(ProfileCreateEvent.class, testConsumerGroupId);
    }

    @Bean
    public ConsumerFactory<String, ProfileUpdatedEvent> testProfileUpdatedEventConsumerFactory(String testConsumerGroupId) {
        return createConsumerFactory(ProfileUpdatedEvent.class, testConsumerGroupId);
    }

    @Bean
    public ConsumerFactory<String, ProfileDeleteEvent> testProfileDeleteEventConsumerFactory(String testConsumerGroupId) {
        return createConsumerFactory(ProfileDeleteEvent.class, testConsumerGroupId);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileCreateEvent> testProfileCreateKafkaListenerContainerFactory(String testConsumerGroupId) {
        return createListenerFactory(testProfileCreateEventConsumerFactory(testConsumerGroupId));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileUpdatedEvent> testProfileUpdatedKafkaListenerContainerFactory(String testConsumerGroupId) {
        return createListenerFactory(testProfileUpdatedEventConsumerFactory(testConsumerGroupId));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> testProfileDeleteKafkaListenerContainerFactory(String testConsumerGroupId) {
        return createListenerFactory(testProfileDeleteEventConsumerFactory(testConsumerGroupId));
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
            containerFactory = "testProfileCreateKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileCreated(
                ProfileCreateEvent event,
                org.springframework.kafka.support.Acknowledgment acknowledgment) {
            try {
                log.info("Test consumer received ProfileCreateEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId());
                profileCreatedEvents.add(event);

                // Manual acknowledgment
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            } catch (Exception e) {
                log.error("Error processing ProfileCreateEvent: {}", event, e);
            }
        }

        @KafkaListener(
            topics = "${kafka.topics.profile-events.updated}",
            containerFactory = "testProfileUpdatedKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileUpdated(
                ProfileUpdatedEvent event,
                org.springframework.kafka.support.Acknowledgment acknowledgment) {
            try {
                log.info("Test consumer received ProfileUpdatedEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId());
                profileUpdatedEvents.add(event);

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            } catch (Exception e) {
                log.error("Error processing ProfileUpdatedEvent: {}", event, e);
            }
        }

        @KafkaListener(
            topics = "${kafka.topics.profile-events.deleted}",
            containerFactory = "testProfileDeleteKafkaListenerContainerFactory",
            autoStartup = "true"
        )
        public void consumeProfileDeleted(
                ProfileDeleteEvent event,
                org.springframework.kafka.support.Acknowledgment acknowledgment) {
            try {
                log.info("Test consumer received ProfileDeleteEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId());
                profileDeletedEvents.add(event);

                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            } catch (Exception e) {
                log.error("Error processing ProfileDeleteEvent: {}", event, e);
            }
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

        /**
         * Get collected ProfileCreateEvent events (thread-safe copy)
         */
        public List<ProfileCreateEvent> getProfileCreatedEvents() {
            synchronized (profileCreatedEvents) {
                return new ArrayList<>(profileCreatedEvents);
            }
        }

        /**
         * Get collected ProfileUpdatedEvent events (thread-safe copy)
         */
        public List<ProfileUpdatedEvent> getProfileUpdatedEvents() {
            synchronized (profileUpdatedEvents) {
                return new ArrayList<>(profileUpdatedEvents);
            }
        }

        /**
         * Get collected ProfileDeleteEvent events (thread-safe copy)
         */
        public List<ProfileDeleteEvent> getProfileDeletedEvents() {
            synchronized (profileDeletedEvents) {
                return new ArrayList<>(profileDeletedEvents);
            }
        }
    }
}
