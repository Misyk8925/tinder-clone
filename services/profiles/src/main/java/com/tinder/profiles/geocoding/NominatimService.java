package com.tinder.profiles.geocoding;

// NominatimService.java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Service
public class NominatimService {
    private final WebClient nominatimClient;

    @Value("${app.geocoding.country-codes:}")
    private String countryCodes;

    @Value("${app.geocoding.timeout-ms:3000}")
    private long timeoutMs;
    @Value("${app.geocoding.retries:5}")
    private int retries;

    // Explicit constructor with @Qualifier
    public NominatimService(@Qualifier("nominatimWebClient") WebClient nominatimClient) {
        this.nominatimClient = nominatimClient;
    }

    public Optional<GeoPoint> geocodeCity(String city) {
        if (city == null || city.isBlank()) return Optional.empty();

        try {
            NominatimResult[] results = nominatimClient.get()
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
                    .bodyToMono(NominatimResult[].class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorReturn(new NominatimResult[0])
                    .block();

            if (results != null && results.length > 0) {
                return Optional.of(results[0].toPoint());
            }
            return Optional.empty();
        } catch (Exception ex) {
            if (retries > 0) {
                retries--;
                return geocodeCity(city);
            }
            return Optional.empty();
        }
    }

    public record GeoPoint(double lon, double lat) {}
    public record NominatimResult(String lon, String lat) {
        GeoPoint toPoint() { return new GeoPoint(Double.parseDouble(lon), Double.parseDouble(lat)); }
    }
}