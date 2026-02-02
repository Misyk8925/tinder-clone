package com.tinder.deck.adapters;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import io.github.resilience4j.bulkhead.SemaphoreBulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilesHttp {
    private static final Logger log = LoggerFactory.getLogger(ProfilesHttp.class);
    private final WebClient profilesWebClient;
    private final CircuitBreaker profilesCircuitBreaker;
    private final SemaphoreBulkhead profilesBulkhead;

    @Value("${deck.clients.profiles.timeout-ms:1500}")
    private long timeoutMs;

    @Value("${deck.clients.profiles.retry.max-attempts:2}")
    private int maxRetries;

    @Value("${deck.clients.profiles.retry.backoff-ms:200}")
    private long retryBackoffMs;

    public Flux<SharedProfileDto> searchProfiles(UUID viewerId, SharedPreferencesDto preferences, int limit) {
        // Use default preferences if null
        if (preferences == null) {
            log.warn("Preferences is null for viewer {}, using defaults", viewerId);
            preferences = new SharedPreferencesDto(18, 50, "ANY", 100);
        }

        log.debug("Calling profiles service /search with viewerId={}, gender={}, minAge={}, maxAge={}, maxRange={}, limit={}",
                viewerId, preferences.gender(), preferences.minAge(), preferences.maxAge(), preferences.maxRange(), limit);

        final SharedPreferencesDto finalPrefs = preferences;
        return profilesWebClient.get()
                .uri(uri -> {
                    java.net.URI built = uri.path("/search")
                            .queryParam("viewerId", viewerId)
                            .queryParam("gender", finalPrefs.gender())
                            .queryParam("minAge", finalPrefs.minAge())
                            .queryParam("maxAge", finalPrefs.maxAge())
                            .queryParam("maxRange", finalPrefs.maxRange())
                            .queryParam("limit", limit)
                            .build();
                    log.debug("Built URI: {}", built);
                    return built;
                })
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(buildRetry("searchProfiles"))
                .transformDeferred(BulkheadOperator.of(profilesBulkhead))
                .transformDeferred(CircuitBreakerOperator.of(profilesCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, throwable -> {
                    log.warn("Profiles circuit breaker open (searchProfiles). Returning empty result.");
                    return Flux.empty();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (searchProfiles). Returning empty result. Cause: {}", throwable.toString());
                    return Flux.empty();
                });
    }


    public Flux<SharedProfileDto> getActiveUsers() {
        return profilesWebClient.get()
                .uri(uri -> uri.path("/active")
                        .build())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(buildRetry("getActiveUsers"))
                .transformDeferred(BulkheadOperator.of(profilesBulkhead))
                .transformDeferred(CircuitBreakerOperator.of(profilesCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, throwable -> {
                    log.warn("Profiles circuit breaker open (getActiveUsers). Returning empty result.");
                    return Flux.empty();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (getActiveUsers). Returning empty result. Cause: {}", throwable.toString());
                    return Flux.empty();
                });
    }

    /**
     * Fetch a single profile by id from Profiles service
     */
    public Mono<SharedProfileDto> getProfile(UUID id) {
        return profilesWebClient.get()
                .uri("/{id}", id)
                .retrieve()
                .toEntity(SharedProfileDto.class)
                .map(ResponseEntity::getBody)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(buildRetry("getProfile"))
                .transformDeferred(BulkheadOperator.of(profilesBulkhead))
                .transformDeferred(CircuitBreakerOperator.of(profilesCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, throwable -> {
                    log.warn("Profiles circuit breaker open (getProfile {}). Returning empty result.", id);
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (getProfile {}). Cause: {}", id, throwable.toString());
                    return Mono.empty();
                });
    }

    /**
     * Fetch multiple profiles by IDs (for preferences cache)
     * Calls /internal/by-ids endpoint with comma-separated IDs
     */
    public Flux<SharedProfileDto> getProfilesByIds(java.util.List<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            log.debug("Empty profile IDs list, returning empty flux");
            return Flux.empty();
        }

        // Convert UUIDs to comma-separated string
        String idsParam = profileIds.stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(","));

        log.debug("Fetching {} profiles by IDs", profileIds.size());

        return profilesWebClient.get()
                .uri(uri -> uri.path("/by-ids")
                        .queryParam("ids", idsParam)
                        .build())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .retryWhen(buildRetry("getProfilesByIds"))
                .transformDeferred(BulkheadOperator.of(profilesBulkhead))
                .transformDeferred(CircuitBreakerOperator.of(profilesCircuitBreaker))
                .onErrorResume(CallNotPermittedException.class, throwable -> {
                    log.warn("Profiles circuit breaker open (getProfilesByIds). Returning empty result.");
                    return Flux.empty();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to fetch profiles by IDs: {}", throwable.toString());
                    return Flux.empty();
                });
    }

    private Retry buildRetry(String operation) {
        return Retry.backoff(maxRetries, Duration.ofMillis(retryBackoffMs))
                .jitter(0.5d)
                .filter(throwable -> !(throwable instanceof CallNotPermittedException))
                .doBeforeRetry(retrySignal -> log.warn(
                        "Retrying profiles service call ({}), attempt {} due to {}",
                        operation,
                        retrySignal.totalRetries() + 1,
                        retrySignal.failure() instanceof TimeoutException
                                ? "timeout"
                                : retrySignal.failure().toString()
                ));
    }
}
