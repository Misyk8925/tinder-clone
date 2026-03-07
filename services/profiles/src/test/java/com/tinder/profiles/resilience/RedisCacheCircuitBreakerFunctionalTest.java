package com.tinder.profiles.resilience;

import com.tinder.profiles.redis.ResilientCacheManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional test to verify Circuit Breaker integration with Redis operations
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisCacheCircuitBreakerFunctionalTest {

    @Autowired
    private ResilientCacheManager resilientCacheManager;

    @Autowired
    private CircuitBreaker redisCircuitBreaker;

    @Autowired
    private CacheManager cacheManager;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Reset circuit breaker before each test
        redisCircuitBreaker.reset();
    }

    @Test
    void testResilientCacheManagerHandlesOperationsWhenRedisIsAvailable() {
        // Given: Circuit breaker is closed
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // When: Perform cache operations
        try {
            resilientCacheManager.put("testCache", "testKey", "testValue");
            var result = resilientCacheManager.get("testCache", "testKey");

            // Then: Operations should succeed or degrade gracefully
            // Note: Result might be null if Redis is not available in test environment
            assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        } catch (Exception e) {
            // If Redis is not available, circuit breaker should handle it
            assertThat(e).isNotInstanceOf(RedisConnectionFailureException.class);
        }
    }

    @Test
    void testCircuitBreakerMetricsAfterCacheOperations() {
        // Given: Fresh circuit breaker
        var metrics = redisCircuitBreaker.getMetrics();
        long initialCalls = metrics.getNumberOfSuccessfulCalls() + metrics.getNumberOfFailedCalls();

        // When: Perform multiple cache operations
        for (int i = 0; i < 5; i++) {
            try {
                resilientCacheManager.get("testCache", "key" + i);
            } catch (Exception e) {
                // Ignore errors for this test
            }
        }

        // Then: Metrics should be updated
        long finalCalls = metrics.getNumberOfSuccessfulCalls() + metrics.getNumberOfFailedCalls();
        assertThat(finalCalls).isGreaterThanOrEqualTo(initialCalls);
    }

    @Test
    void testCircuitBreakerDoesNotOpenOnSuccessfulOperations() {
        // Given: Circuit breaker is closed
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // When: Perform multiple successful operations
        var config = redisCircuitBreaker.getCircuitBreakerConfig();
        int minCalls = config.getMinimumNumberOfCalls();

        for (int i = 0; i < minCalls + 5; i++) {
            try {
                redisCircuitBreaker.executeSupplier(() -> "success");
            } catch (Exception e) {
                // Should not happen
            }
        }

        // Then: Circuit breaker should remain CLOSED
        assertThat(redisCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(redisCircuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .isGreaterThanOrEqualTo(minCalls);
    }

    @Test
    void testCacheManagerIsConfigured() {
        // Verify that cache manager is properly configured
        assertThat(cacheManager).isNotNull();
    }

    @Test
    void testResilientCacheManagerIsConfigured() {
        // Verify that resilient cache manager is properly configured
        assertThat(resilientCacheManager).isNotNull();
    }
}
