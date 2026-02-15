package com.tinder.profiles.kafka;

import com.tinder.profiles.config.OutboxPublisherProperties;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Kafka producer wrapper for background outbox publishing.
 * Uses circuit breaker for resource protection and throws on failure so outbox can retry.
 */
@Slf4j
@Service
public class ResilientProfileEventProducer {

    private final KafkaTemplate<String, ProfileUpdatedEvent> profileUpdatedEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileDeleteEvent> profileDeleteEventKafkaTemplate;
    private final KafkaTemplate<String, ProfileCreateEvent> profileCreateEventKafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final long sendTimeoutMs;

    public ResilientProfileEventProducer(
            KafkaTemplate<String, ProfileUpdatedEvent> profileUpdatedEventKafkaTemplate,
            KafkaTemplate<String, ProfileDeleteEvent> profileDeleteEventKafkaTemplate,
            KafkaTemplate<String, ProfileCreateEvent> profileCreateEventKafkaTemplate,
            @Qualifier("kafkaCircuitBreaker") CircuitBreaker circuitBreaker,
            OutboxPublisherProperties outboxPublisherProperties
    ) {
        this.profileUpdatedEventKafkaTemplate = profileUpdatedEventKafkaTemplate;
        this.profileDeleteEventKafkaTemplate = profileDeleteEventKafkaTemplate;
        this.profileCreateEventKafkaTemplate = profileCreateEventKafkaTemplate;
        this.circuitBreaker = circuitBreaker;
        this.sendTimeoutMs = outboxPublisherProperties.getSendTimeoutMs();
    }

    public void sendProfileUpdateEvent(ProfileUpdatedEvent event, String key, String topic) {
        log.debug("Sending profile update event to topic: {} with key: {}", topic, key);

        executeWithCircuitBreaker(
                () -> profileUpdatedEventKafkaTemplate.send(topic, key, event),
                "ProfileUpdateEvent",
                topic,
                key
        );
    }

    public void sendProfileDeleteEvent(ProfileDeleteEvent event, String key, String topic) {
        log.debug("Sending profile delete event to topic: {} with key: {}", topic, key);

        executeWithCircuitBreaker(
                () -> profileDeleteEventKafkaTemplate.send(topic, key, event),
                "ProfileDeleteEvent",
                topic,
                key
        );
    }

    public void sendProfileCreateEvent(ProfileCreateEvent event, String key, String topic) {
        log.debug("Sending profile create event to topic: {} with key: {}", topic, key);

        executeWithCircuitBreaker(
                () -> profileCreateEventKafkaTemplate.send(topic, key, event),
                "ProfileCreateEvent",
                topic,
                key
        );
    }

    private <T> void executeWithCircuitBreaker(
            Supplier<CompletionStage<T>> supplier,
            String eventType,
            String topic,
            String key
    ) {
        try {
            Supplier<CompletionStage<T>> decoratedSupplier =
                    CircuitBreaker.decorateCompletionStage(circuitBreaker, supplier);

            decoratedSupplier.get()
                    .toCompletableFuture()
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

            log.debug("Successfully sent {} to topic '{}' with key '{}'", eventType, topic, key);
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Throwable rootCause = unwrap(e);
            log.error("Failed to send {} to topic '{}' with key '{}': {} - {}",
                    eventType, topic, key, rootCause.getClass().getSimpleName(), rootCause.getMessage());
            throw new IllegalStateException("Kafka send failed for " + eventType, rootCause);
        } catch (Exception e) {
            Throwable rootCause = unwrap(e);
            log.error("Failed to send {} to topic '{}' with key '{}': {} - {}",
                    eventType, topic, key, rootCause.getClass().getSimpleName(), rootCause.getMessage());
            throw new IllegalStateException("Kafka send failed for " + eventType, rootCause);
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return executionException.getCause();
        }
        if (throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
