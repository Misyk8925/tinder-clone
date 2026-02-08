package com.tinder.profiles.kafka;

import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import com.tinder.profiles.resilience.ResilienceSchedulerConfig;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Resilient wrapper for Kafka producer operations with circuit breaker, bulkhead, and retry patterns.
 * Provides fail-safe event publishing that degrades gracefully when Kafka is unavailable.
 */
@Slf4j
@Service
public class ResilientProfileEventProducer {

    private final KafkaTemplate<String, ProfileUpdatedEvent> profileUpdatedEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileDeleteEvent> profileDeleteEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileCreateEvent> profileCreateEventKafkaTemplate;
    private final ScheduledExecutorService retryScheduler;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Retry retry;

    public ResilientProfileEventProducer(
            KafkaTemplate<String, ProfileUpdatedEvent> profileUpdatedEventKafkaTemplate,
            KafkaTemplate<String, ProfileDeleteEvent> profileDeleteEventKafkaTemplate,
            KafkaTemplate<String, ProfileCreateEvent> profileCreateEventKafkaTemplate,
            ScheduledExecutorService retryScheduler,
            @Qualifier("kafkaCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("kafkaBulkhead") Bulkhead bulkhead,
            @Qualifier("kafkaRetry") Retry retry
    ) {
        this.profileUpdatedEventKafkaTemplate = profileUpdatedEventKafkaTemplate;
        this.profileDeleteEventKafkaTemplate = profileDeleteEventKafkaTemplate;
        this.profileCreateEventKafkaTemplate = profileCreateEventKafkaTemplate;
        this.retryScheduler = retryScheduler;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.retry = retry;
    }

    /**
     * Send profile update event with resilience patterns
     */
    public void sendProfileUpdateEvent(
            ProfileUpdatedEvent event,
            String key,
            String topic
    ) {
        log.info("Sending profile update event to topic: {} with key: {} and event: {}", topic, key, event);

        executeResilient(
                    () -> profileUpdatedEventKafkaTemplate.send(topic, key, event),
                            "ProfileUpdateEvent",
                            topic,
                            key);

    }

    /**
     * Send profile delete event with resilience patterns
     */
    public void sendProfileDeleteEvent(
           ProfileDeleteEvent event,
           String key,
           String topic
    ){
        log.info("Sending profile delete event to topic: {} with key: {} and event: {}", topic, key, event);

        executeResilient(
                () -> profileDeleteEventKafkaTemplate.send(topic, key, event),
                "ProfileDeleteEvent",
                topic,
                key);
    }

    /**
     * Send profile create event with resilience patterns
     */
    public void sendProfileCreateEvent(
           ProfileCreateEvent event,
           String key,
           String topic
    ){
        log.info("Sending profile create event to topic: {} with key: {} and event: {}", topic, key, event);

        executeResilient(
                () -> profileCreateEventKafkaTemplate.send(topic, key, event),
                "ProfileCreateEvent",
                topic,
                key);
    }

    /**
     * Execute Kafka operation with circuit breaker, bulkhead, and retry
     *
     * @param supplier the Kafka send operation
     * @param eventType event type name for logging
     * @param topic Kafka topic
     * @param key message key
     */

    private <T> void executeResilient(Supplier<CompletionStage<T>> supplier,
                                      String eventType,
                                      String topic,
                                      String key) {
        // Bulkhead (sync) as a simple concurrency gate around starting the async operation
        Bulkhead bulk = this.bulkhead;

        if (!bulk.tryAcquirePermission()) {
            log.warn("Bulkhead rejected {} for topic '{}' key '{}': too many concurrent in-flight sends",
                    eventType, topic, key);
            return; // fail-open: drop
        }

        try {
            // CircuitBreaker + Retry for async completion
            Supplier<CompletionStage<T>> cbDecorated =
                    CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

            Supplier<CompletionStage<T>> retryDecorated =
                    Retry.decorateCompletionStage(retry, retryScheduler, cbDecorated);

            retryDecorated.get().whenComplete((res, ex) -> {
                // IMPORTANT: release bulkhead when async completes
                bulk.releasePermission();

                if (ex != null) {
                    log.error("Failed to send {} to topic '{}' with key '{}': {} - {}. Event lost (fail-open).",
                            eventType, topic, key, ex.getClass().getSimpleName(), ex.getMessage(), ex);
                } else {
                    log.debug("Successfully sent {} to topic '{}' with key '{}'", eventType, topic, key);
                }
            });

        } catch (Exception e) {
            // release if starting the async operation throws synchronously
            bulk.releasePermission();
            log.error("Failed to start send {} to topic '{}' with key '{}': {} - {}. Event lost (fail-open).",
                    eventType, topic, key, e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
