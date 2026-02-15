package com.tinder.profiles.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j configuration that uses properties from application.yml.
 * The registries are auto-configured by Spring Boot based on resilience4j.* properties.
 * This class only adds event listeners and creates named beans for dependency injection.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ResilienceConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final RetryRegistry retryRegistry;

    /**
     * Register event listeners for circuit breakers after context initialization
     */
    @PostConstruct
    public void registerEventListeners() {
        log.info("Registering Resilience4j event listeners");

        // Redis Circuit Breaker listeners
        circuitBreakerRegistry.circuitBreaker("redisCache").getEventPublisher()
                .onStateTransition(event ->
                    log.warn("Redis Circuit Breaker state changed: from {} to {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event ->
                    log.error("Redis Circuit Breaker error: {}", event.getThrowable().getMessage()))
                .onSuccess(event ->
                    log.trace("Redis Circuit Breaker success"));

        // Kafka Circuit Breaker listeners
        circuitBreakerRegistry.circuitBreaker("kafkaProducer").getEventPublisher()
                .onStateTransition(event ->
                    log.warn("Kafka Producer Circuit Breaker state changed: from {} to {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event ->
                    log.error("Kafka Producer Circuit Breaker error: {}", event.getThrowable().getMessage()))
                .onSuccess(event ->
                    log.trace("Kafka Producer Circuit Breaker success"));

        // Nominatim Circuit Breaker listeners
        circuitBreakerRegistry.circuitBreaker("nominatimClient").getEventPublisher()
                .onStateTransition(event ->
                    log.warn("Nominatim Circuit Breaker state changed: from {} to {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event ->
                    log.error("Nominatim Circuit Breaker error: {}", event.getThrowable().getMessage()))
                .onSuccess(event ->
                    log.trace("Nominatim Circuit Breaker success"));
    }

    /**
     * Named circuit breaker beans for dependency injection
     */
    @Bean
    public CircuitBreaker redisCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("redisCache");
    }

    @Bean
    public CircuitBreaker kafkaCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("kafkaProducer");
    }

    @Bean
    public CircuitBreaker nominatimCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("nominatimClient");
    }

    /**
     * Named bulkhead beans for dependency injection
     */
    @Bean
    public Bulkhead redisBulkhead() {
        return bulkheadRegistry.bulkhead("redisCache");
    }

    @Bean
    public Bulkhead nominatimBulkhead() {
        return bulkheadRegistry.bulkhead("nominatimClient");
    }

    /**
     * Named retry beans for dependency injection
     */
    @Bean
    public Retry redisRetry() {
        return retryRegistry.retry("redisCache");
    }

    @Bean
    public Retry nominatimRetry() {
        return retryRegistry.retry("nominatimClient");
    }
}
