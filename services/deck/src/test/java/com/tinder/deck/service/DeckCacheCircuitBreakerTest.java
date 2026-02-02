package com.tinder.deck.service;

import io.github.resilience4j.bulkhead.SemaphoreBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeckCacheCircuitBreakerTest {

    @Test
    void sizeReturnsZeroWhenCircuitOpen() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.opsForZSet()).thenThrow(new IllegalStateException("redis should not be called"));

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        SemaphoreBulkheadRegistry bulkheadRegistry = SemaphoreBulkheadRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("redisCache");
        circuitBreaker.transitionToOpenState();

        DeckCache cache = new DeckCache(
                redis,
                circuitBreaker,
                bulkheadRegistry.bulkhead("redisCache")
        );
        ReflectionTestUtils.setField(cache, "redisTimeoutMs", 10L);
        ReflectionTestUtils.setField(cache, "redisMaxRetries", 0);
        ReflectionTestUtils.setField(cache, "redisRetryBackoffMs", 1L);

        StepVerifier.create(cache.size(UUID.randomUUID()))
                .expectNext(0L)
                .verifyComplete();
    }
}
