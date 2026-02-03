package com.tinder.deck.adapters;

import io.github.resilience4j.bulkhead.SemaphoreBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class SwipesHttp {
    private static final Logger log = LoggerFactory.getLogger(SwipesHttp.class);

    private final WebClient swipesWebClient;
    private final CircuitBreaker swipesCircuitBreaker;
    private final SemaphoreBulkhead swipesBulkhead;

    @Value("${deck.clients.swipes.timeout-ms:1500}")
    private long timeoutMs;

    @Value("${deck.clients.swipes.retry.max-attempts:2}")
    private int maxRetries;

    @Value("${deck.clients.swipes.retry.backoff-ms:200}")
    private long retryBackoffMs;

    /**
     * Batch check if swipes exist between viewer and candidates
     * Calls /internal/between/batch endpoint (not secured)
     */
    public Mono<Map<UUID, Boolean>> betweenBatch(UUID viewerId, List<UUID> candidateIds) {
        return swipesWebClient.post()
                .uri("/between/batch?viewerId={id}", viewerId)
                .bodyValue(candidateIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<UUID, Boolean>>() {})
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(buildRetry())
                .transformDeferred(BulkheadOperator.of(swipesBulkhead))
                .transformDeferred(CircuitBreakerOperator.of(swipesCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, throwable -> {
                    log.warn("Swipes circuit breaker open (betweenBatch). Returning empty swipe map.");
                    return Mono.just(Map.of());
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to call swipes service (betweenBatch). Returning empty swipe map. Cause: {}",
                            throwable.toString());
                    return Mono.just(Map.of());
                });
    }

    private Retry buildRetry() {
        return Retry.backoff(maxRetries, Duration.ofMillis(retryBackoffMs))
                .jitter(0.5d)
                .filter(throwable -> !(throwable instanceof CallNotPermittedException))
                .doBeforeRetry(retrySignal -> log.warn(
                        "Retrying swipes service call, attempt {} due to {}",
                        retrySignal.totalRetries() + 1,
                        retrySignal.failure() instanceof TimeoutException
                                ? "timeout"
                                : retrySignal.failure().toString()
                ));
    }
}
