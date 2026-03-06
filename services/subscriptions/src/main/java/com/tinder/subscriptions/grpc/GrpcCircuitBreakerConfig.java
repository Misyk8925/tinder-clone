package com.tinder.subscriptions.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcCircuitBreakerConfig {

    @Bean
    public CircuitBreaker profilesGrpcCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("profiles-grpc");
    }
}
