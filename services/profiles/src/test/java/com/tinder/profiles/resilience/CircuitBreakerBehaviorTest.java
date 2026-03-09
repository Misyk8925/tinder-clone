package com.tinder.profiles.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test to verify Circuit Breaker behavior under failure scenarios
 */
@SpringBootTest
@ActiveProfiles("test")
class CircuitBreakerBehaviorTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CircuitBreaker redisCircuitBreaker;

    @Autowired
    private CircuitBreaker kafkaCircuitBreaker;

    @Autowired
    private CircuitBreaker nominatimCircuitBreaker;

    @BeforeEach
    void setUp() {
        // Reset all circuit breakers to CLOSED state before each test
        redisCircuitBreaker.reset();
        kafkaCircuitBreaker.reset();
        nominatimCircuitBreaker.reset();
    }

    @Test
    void testCircuitBreakerOpensAfterFailures() {
        // Given: Circuit breaker is CLOSED
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Get configuration
        var config = redisCircuitBreaker.getCircuitBreakerConfig();
        int minimumCalls = config.getMinimumNumberOfCalls();

        // When: Trigger enough failures to open the circuit
        for (int i = 0; i < minimumCalls; i++) {
            try {
                redisCircuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("Simulated Redis failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }

        // Then: Circuit breaker should be OPEN
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void testCircuitBreakerMetrics() {
        // Given: Fresh circuit breaker
        CircuitBreaker cb = redisCircuitBreaker;
        var metrics = cb.getMetrics();

        // When: Execute some successful calls
        for (int i = 0; i < 5; i++) {
            cb.executeSupplier(() -> "success");
        }

        // Then: Metrics should reflect successful calls
        assertThat(metrics.getNumberOfSuccessfulCalls()).isGreaterThanOrEqualTo(5);
        assertThat(metrics.getNumberOfFailedCalls()).isEqualTo(0);
    }

    @Test
    void testCircuitBreakerTransitionsToHalfOpen() throws InterruptedException {
        // Given: Circuit breaker is OPEN
        var config = nominatimCircuitBreaker.getCircuitBreakerConfig();
        int minimumCalls = config.getMinimumNumberOfCalls();

        // Trigger failures to open circuit
        for (int i = 0; i < minimumCalls; i++) {
            try {
                nominatimCircuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("Simulated Nominatim failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }

        assertThat(nominatimCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When: Wait for automatic transition to HALF_OPEN
        // Note: In test environment, waitDurationInOpenState might be long
        // We'll manually transition for testing purposes
        nominatimCircuitBreaker.transitionToHalfOpenState();

        // Then: Circuit breaker should be HALF_OPEN
        assertThat(nominatimCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void testCircuitBreakerRecoveryFromHalfOpen() {
        // Given: Circuit breaker in HALF_OPEN state
        // First, we need to open it by triggering failures
        var config = nominatimCircuitBreaker.getCircuitBreakerConfig();
        int minimumCalls = config.getMinimumNumberOfCalls();

        // Trigger failures to open circuit
        for (int i = 0; i < minimumCalls; i++) {
            try {
                nominatimCircuitBreaker.executeSupplier(() -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception e) {
                // Expected
            }
        }

        // Now transition to HALF_OPEN
        nominatimCircuitBreaker.transitionToHalfOpenState();
        assertThat(nominatimCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        int permittedCalls = config.getPermittedNumberOfCallsInHalfOpenState();

        // When: Execute successful calls in HALF_OPEN state
        for (int i = 0; i < permittedCalls; i++) {
            nominatimCircuitBreaker.executeSupplier(() -> "success");
        }

        // Then: Circuit breaker should transition back to CLOSED
        assertThat(nominatimCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testKafkaCircuitBreakerConfiguration() {
        // Verify Kafka-specific configuration
        var config = kafkaCircuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(50);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(60.0f);
        assertThat(config.getSlowCallDurationThreshold().getSeconds()).isEqualTo(3);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(10);
    }

    @Test
    void testRedisCircuitBreakerConfiguration() {
        // Verify Redis-specific configuration
        var config = redisCircuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        assertThat(config.getSlowCallDurationThreshold().toMillis()).isEqualTo(1000);
    }

    @Test
    void testNominatimCircuitBreakerConfiguration() {
        // Verify Nominatim-specific configuration
        var config = nominatimCircuitBreaker.getCircuitBreakerConfig();

        assertThat(config.getFailureRateThreshold()).isEqualTo(60.0f);
        assertThat(config.getSlowCallDurationThreshold().toMillis()).isEqualTo(500);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }

    @Test
    void testCircuitBreakerEventPublisher() {
        // Test that event publisher is working
        final boolean[] eventReceived = {false};

        redisCircuitBreaker.getEventPublisher()
                .onSuccess(event -> eventReceived[0] = true);

        // Execute successful call
        redisCircuitBreaker.executeSupplier(() -> "test");

        // Verify event was published
        assertThat(eventReceived[0]).isTrue();
    }

    @Test
    void testAllCircuitBreakersRegistered() {
        // Verify all circuit breakers are registered
        assertThat(circuitBreakerRegistry.getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName)
                .contains("redisCache", "kafkaProducer", "nominatimClient");
    }
}
