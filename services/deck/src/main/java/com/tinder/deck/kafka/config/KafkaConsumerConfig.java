package com.tinder.deck.kafka.config;

import com.tinder.deck.kafka.dto.ProfileDeleteEvent;
import com.tinder.deck.kafka.dto.ProfileUpdateEvent;
import com.tinder.deck.kafka.dto.SwipeCreatedEvent;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import java.util.concurrent.ThreadLocalRandom;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for ProfileEvent consumption
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.listener.concurrency:3}")
    private int concurrency;

    @Value("${deck.kafka.error-handler.max-retries:5}")
    private int errorHandlerMaxRetries;

    @Value("${deck.kafka.error-handler.initial-interval:1s}")
    private Duration errorHandlerInitialInterval;

    @Value("${deck.kafka.error-handler.multiplier:2.0}")
    private double errorHandlerMultiplier;

    @Value("${deck.kafka.error-handler.max-interval:30s}")
    private Duration errorHandlerMaxInterval;

    @Value("${deck.kafka.error-handler.jitter:0.5}")
    private double errorHandlerJitter;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
        BackOff backOff = new ExponentialJitterBackOffWithMaxRetries(
                errorHandlerMaxRetries,
                errorHandlerInitialInterval.toMillis(),
                errorHandlerMultiplier,
                errorHandlerMaxInterval.toMillis(),
                errorHandlerJitter
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    /**
     * Consumer factory for ProfileEvent deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileUpdateEvent> profileEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.deck.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileUpdateEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Consumer factory for SwipeCreatedEvent deserialization
     */
    @Bean
    public ConsumerFactory<String, SwipeCreatedEvent> swipeEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-swipe");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.deck.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SwipeCreatedEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Consumer factory for ProfileDeleteEvent deserialization
     */
    @Bean
    public ConsumerFactory<String, ProfileDeleteEvent> profileDeleteEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-delete");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.tinder.deck.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProfileDeleteEvent.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Performance tuning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Listener container factory with manual acknowledgment
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileUpdateEvent> kafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileUpdateEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    /**
     * Listener container factory for SwipeCreatedEvent with manual acknowledgment
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SwipeCreatedEvent> swipeKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, SwipeCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(swipeEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    /**
     * Listener container factory for ProfileDeleteEvent with manual acknowledgment
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> deleteKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ProfileDeleteEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(profileDeleteEventConsumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    /**
     * Exponential backoff with bounded retries and jitter.
     * Stops after {@code maxRetries} retries; total attempts = 1 (initial) + maxRetries.
     */
    private static final class ExponentialJitterBackOffWithMaxRetries implements BackOff {
        private final int maxRetries;
        private final long initialIntervalMs;
        private final double multiplier;
        private final long maxIntervalMs;
        private final double jitter;

        private ExponentialJitterBackOffWithMaxRetries(
                int maxRetries,
                long initialIntervalMs,
                double multiplier,
                long maxIntervalMs,
                double jitter) {
            this.maxRetries = Math.max(0, maxRetries);
            this.initialIntervalMs = Math.max(0L, initialIntervalMs);
            this.multiplier = Math.max(1.0, multiplier);
            this.maxIntervalMs = Math.max(0L, maxIntervalMs);
            this.jitter = Math.max(0.0, jitter);
        }

        @Override
        public BackOffExecution start() {
            return new BackOffExecution() {
                private int retries;

                @Override
                public long nextBackOff() {
                    if (retries++ >= maxRetries) {
                        return STOP;
                    }

                    long interval = computeIntervalMs(retries);
                    if (jitter <= 0.0 || interval <= 0L) {
                        return interval;
                    }

                    double delta = interval * jitter;
                    double min = Math.max(0.0, interval - delta);
                    double max = interval + delta;
                    return (long) (min + ThreadLocalRandom.current().nextDouble(max - min));
                }
            };
        }

        private long computeIntervalMs(int attempt) {
            if (attempt <= 0) {
                return initialIntervalMs;
            }
            double raw = initialIntervalMs * Math.pow(multiplier, attempt - 1);
            long capped = maxIntervalMs > 0L ? Math.min((long) raw, maxIntervalMs) : (long) raw;
            return Math.max(0L, capped);
        }
    }
}
