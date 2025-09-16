package com.tinder.profiles.geocoding;

// NominatimService.java
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NominatimService {
    private final WebClient nominatimClient;

    @Value("${app.geocoding.country-codes:}")
    private String countryCodes;

    @Value("${app.geocoding.timeout-ms:3000}")
    private long timeoutMs;

    public Optional<GeoPoint> geocodeCity(String city) {
        if (city == null || city.isBlank()) return Optional.empty();
        return nominatimClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", city.trim())
                        .queryParam("format", "jsonv2")
                        .queryParam("limit", 1)
                        .queryParam("addressdetails", 1)
                        .queryParamIfPresent("countrycodes",
                                countryCodes == null || countryCodes.isBlank() ? Optional.empty() : Optional.of(countryCodes))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        status -> status.isError(),
                        response -> response.createException()
                )
                .bodyToMono(NominatimResult[].class)
                .timeout(Duration.ofMillis(timeoutMs))
                .onErrorResume(ex -> Mono.just(new NominatimResult[0]))
                .map(arr -> arr.length > 0
                        ? Optional.of(arr[0].toPoint())
                        : Optional.<GeoPoint>empty())
                .defaultIfEmpty(Optional.<GeoPoint>empty())
                .block();
    }

    public record GeoPoint(double lon, double lat) {}
    public record NominatimResult(String lon, String lat) {
        GeoPoint toPoint() { return new GeoPoint(Double.parseDouble(lon), Double.parseDouble(lat)); }
    }
}