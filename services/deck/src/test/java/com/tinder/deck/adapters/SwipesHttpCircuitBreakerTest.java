package com.tinder.deck.adapters;

import io.github.resilience4j.bulkhead.SemaphoreBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SwipesHttpCircuitBreakerTest {

    @Test
    void betweenBatchReturnsEmptyMapWhenCircuitOpen() {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(500).build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        SemaphoreBulkheadRegistry bulkheadRegistry = SemaphoreBulkheadRegistry.ofDefaults();
        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("swipesClient");
        circuitBreaker.transitionToOpenState();

        SwipesHttp client = new SwipesHttp(
                webClient,
                circuitBreaker,
                bulkheadRegistry.bulkhead("swipesClient")
        );
        ReflectionTestUtils.setField(client, "timeoutMs", 10L);
        ReflectionTestUtils.setField(client, "maxRetries", 0);
        ReflectionTestUtils.setField(client, "retryBackoffMs", 1L);

        StepVerifier.create(client.betweenBatch(UUID.randomUUID(), List.of(UUID.randomUUID())))
                .expectNext(Map.of())
                .verifyComplete();
    }
}
