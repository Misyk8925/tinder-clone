package com.tinder.profiles.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Resilience4j circuit breaker configuration
 */
@SpringBootTest
@ActiveProfiles("test")
class ResilienceConfigIntegrationTest {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CircuitBreaker redisCircuitBreaker;

    @Autowired
    private CircuitBreaker kafkaCircuitBreaker;

    @Autowired
    private CircuitBreaker nominatimCircuitBreaker;

    @Test
    void testCircuitBreakerRegistryIsConfigured() {
        assertThat(circuitBreakerRegistry).isNotNull();
    }

    @Test
    void testRedisCircuitBreakerIsConfigured() {
        assertThat(redisCircuitBreaker).isNotNull();
        assertThat(redisCircuitBreaker.getName()).isEqualTo("redisCache");
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testKafkaCircuitBreakerIsConfigured() {
        assertThat(kafkaCircuitBreaker).isNotNull();
        assertThat(kafkaCircuitBreaker.getName()).isEqualTo("kafkaProducer");
        assertThat(kafkaCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testNominatimCircuitBreakerIsConfigured() {
        assertThat(nominatimCircuitBreaker).isNotNull();
        assertThat(nominatimCircuitBreaker.getName()).isEqualTo("nominatimClient");
        assertThat(nominatimCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void testCircuitBreakerConfiguration() {

        var redisConfig = redisCircuitBreaker.getCircuitBreakerConfig();
        assertThat(redisConfig.getSlidingWindowSize()).isGreaterThan(0);
        assertThat(redisConfig.getMinimumNumberOfCalls()).isGreaterThan(0);

        var kafkaConfig = kafkaCircuitBreaker.getCircuitBreakerConfig();
        assertThat(kafkaConfig.getSlidingWindowSize()).isGreaterThan(0);
        assertThat(kafkaConfig.getMinimumNumberOfCalls()).isGreaterThan(0);

        var nominatimConfig = nominatimCircuitBreaker.getCircuitBreakerConfig();
        assertThat(nominatimConfig.getSlidingWindowSize()).isGreaterThan(0);
        assertThat(nominatimConfig.getMinimumNumberOfCalls()).isGreaterThan(0);
    }
}
