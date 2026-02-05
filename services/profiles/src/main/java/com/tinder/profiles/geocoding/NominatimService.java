package com.tinder.profiles.geocoding;

// NominatimService.java
import io.github.resilience4j.bulkhead.internal.SemaphoreBulkhead;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class NominatimService {
    private final WebClient nominatimClient;
    @Value("${app.geocoding.country-codes:}")
    private String countryCodes;

    @Value("${app.geocoding.timeout-ms:3000}")
    private long timeoutMs;

    @Value("${app.geocoding.retries:3}")
    private int maxRetries;

    @Value("${app.geocoding.retry-backoff-ms:400}")
    private long retryBackoffMs;

    // Explicit constructor with @Qualifier
    public NominatimService(
            @Qualifier("nominatimWebClient") WebClient nominatimClient
    ) {
        this.nominatimClient = nominatimClient;

    }

    public Optional<GeoPoint> geocodeCity(String city) {
        if (city == null || city.isBlank()) {
            log.warn("Geocoding request with null or blank city");
            return Optional.empty();
        }

        String trimmedCity = city.trim();
        log.debug("Geocoding city: '{}' with country codes: '{}'", trimmedCity, countryCodes);

        try {
            NominatimResult[] results = nominatimClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", trimmedCity)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("addressdetails", 1)
                            .queryParamIfPresent("countrycodes",
                                    countryCodes == null || countryCodes.isBlank() ? Optional.empty() : Optional.of(countryCodes))
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(NominatimResult[].class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryBackoffMs))
                            .jitter(0.5d)
                            .filter(throwable -> !(throwable instanceof CallNotPermittedException))
                            .filter(throwable -> !(throwable instanceof WebClientResponseException.NotFound))
                            .doBeforeRetry(retrySignal ->
                                log.warn("Retrying geocoding for city '{}', attempt: {}, error: {}",
                                    trimmedCity, retrySignal.totalRetries() + 1, retrySignal.failure().getMessage())))
                    .onErrorResume(CallNotPermittedException.class, throwable -> {
                        log.warn("Geocoding circuit breaker open for city '{}'", trimmedCity);
                        return Mono.just(new NominatimResult[0]);
                    })
                    .doOnError(error -> log.error("Geocoding request failed for city '{}': {}", trimmedCity, error.getMessage()))
                    .onErrorResume(throwable -> {
                        log.error("Error during geocoding for city '{}': {} - {}",
                            trimmedCity, throwable.getClass().getSimpleName(), throwable.getMessage());
                        return Mono.just(new NominatimResult[0]);
                    })
                    .block();

            if (results != null && results.length > 0) {
                log.info("Successfully geocoded city '{}': lat={}, lon={}", trimmedCity, results[0].lat(), results[0].lon());
                return Optional.of(results[0].toPoint());
            }

            log.warn("No geocoding results found for city: '{}'", trimmedCity);
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Unexpected error during geocoding for city '{}': {}", trimmedCity, ex.getMessage(), ex);
            return Optional.empty();
        }
    }

    public record GeoPoint(double lon, double lat) {}

    public record NominatimResult(String lon, String lat) {
        GeoPoint toPoint() {
            return new GeoPoint(Double.parseDouble(lon), Double.parseDouble(lat));
        }
    }
}
