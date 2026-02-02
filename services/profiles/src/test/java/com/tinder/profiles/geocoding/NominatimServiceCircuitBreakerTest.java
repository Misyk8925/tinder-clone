package com.tinder.profiles.geocoding;

import io.github.resilience4j.bulkhead.SemaphoreBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NominatimServiceCircuitBreakerTest {

    @Test
    void geocodeCityReturnsEmptyWhenCircuitOpen() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(500).build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        SemaphoreBulkheadRegistry bulkheadRegistry = SemaphoreBulkheadRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("nominatimClient");
        circuitBreaker.transitionToOpenState();

        NominatimService service = new NominatimService(
                webClient,
                circuitBreaker,
                bulkheadRegistry.bulkhead("nominatimClient")
        );
        ReflectionTestUtils.setField(service, "timeoutMs", 10L);
        ReflectionTestUtils.setField(service, "maxRetries", 0);
        ReflectionTestUtils.setField(service, "retryBackoffMs", 1L);
        ReflectionTestUtils.setField(service, "countryCodes", "");

        Optional<NominatimService.GeoPoint> result = service.geocodeCity("Berlin");

        assertTrue(result.isEmpty());
    }
}
