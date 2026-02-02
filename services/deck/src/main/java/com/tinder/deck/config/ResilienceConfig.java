package com.tinder.deck.config;

import io.github.resilience4j.bulkhead.SemaphoreBulkhead;
import io.github.resilience4j.bulkhead.SemaphoreBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker profilesCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("profilesClient");
    }

    @Bean
    public CircuitBreaker swipesCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("swipesClient");
    }

    @Bean
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("redisCache");
    }

    @Bean
    public SemaphoreBulkhead profilesBulkhead(SemaphoreBulkheadRegistry registry) {
        return registry.bulkhead("profilesClient");
    }

    @Bean
    public SemaphoreBulkhead swipesBulkhead(SemaphoreBulkheadRegistry registry) {
        return registry.bulkhead("swipesClient");
    }

    @Bean
    public SemaphoreBulkhead redisBulkhead(SemaphoreBulkheadRegistry registry) {
        return registry.bulkhead("redisCache");
    }
}
