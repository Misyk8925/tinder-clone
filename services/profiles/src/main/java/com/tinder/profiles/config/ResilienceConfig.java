package com.tinder.profiles.config;

import io.github.resilience4j.bulkhead.SemaphoreBulkhead;
import io.github.resilience4j.bulkhead.SemaphoreBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker nominatimCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("nominatimClient");
    }

    @Bean
    public SemaphoreBulkhead nominatimBulkhead(SemaphoreBulkheadRegistry registry) {
        return registry.bulkhead("nominatimClient");
    }
}
