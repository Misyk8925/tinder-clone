package com.tinder.profiles.geocoding;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NominatimServiceCircuitBreakerTest {

    @Test
    void geocodeCityReturnsEmptyWhenCircuitOpen() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(500)).build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        // Create circuit breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("nominatimClient");
        circuitBreaker.transitionToOpenState();

        // Create bulkhead
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .build();
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        Bulkhead bulkhead = bulkheadRegistry.bulkhead("nominatimClient");

        // Create retry
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(100))
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        Retry retry = retryRegistry.retry("nominatimClient");

        NominatimService service = new NominatimService(
                webClient,
                circuitBreaker,
                bulkhead,
                retry
        );
        ReflectionTestUtils.setField(service, "timeoutMs", 10L);
        ReflectionTestUtils.setField(service, "countryCodes", "");

        Optional<NominatimService.GeoPoint> result = service.geocodeCity("Berlin");

        assertTrue(result.isEmpty());
    }
}
