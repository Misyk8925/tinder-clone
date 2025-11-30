package com.tinder.deck.adapters;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ProfilesHttp {
    private static final Logger log = LoggerFactory.getLogger(ProfilesHttp.class);
    private final WebClient profilesWebClient;
    private static final AtomicReference<String> token = new AtomicReference<>();

    // Initial token refresh on startup (non-blocking)
    @Scheduled(initialDelay = 0, fixedRate = 50000) // Refresh every 50 seconds
    public void refreshTokenScheduled() {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:9080")
                .build();

        webClient.post()
                .uri("/realms/spring/protocol/openid-connect/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("client_id=spring-app&grant_type=password&username=kovalmisha2000@gmail.com&password=koval")
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .subscribe(
                        response -> {
                            String newToken = (String) response.get("access_token");
                            token.set(newToken);
                            log.info("Keycloak token refreshed successfully");
                        },
                        error -> log.error("Failed to refresh Keycloak token: {}", error.toString())
                );
    }

    private String getToken() {
        String currentToken = token.get();
        if (currentToken == null) {
            log.warn("Token is null, returning empty string");
            return "";
        }
        return currentToken;
    }

    public Flux<SharedProfileDto> searchProfiles(UUID viewerId, SharedPreferencesDto preferences, int limit) {
        // Use default preferences if null
        if (preferences == null) {
            log.warn("Preferences is null for viewer {}, using defaults", viewerId);
            preferences = new SharedPreferencesDto(18, 50, "ANY", 100);
        }

        final SharedPreferencesDto finalPrefs = preferences;
        return profilesWebClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("viewerId", viewerId)
                        .queryParam("gender", finalPrefs.gender())
                        .queryParam("minAge", finalPrefs.minAge())
                        .queryParam("maxAge", finalPrefs.maxAge())
                        .queryParam("limit", limit)
                        .build())
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (searchProfiles). Returning empty result. Cause: {}", throwable.toString());
                    return Flux.empty();
                });
    }


    public Flux<SharedProfileDto> getActiveUsers() {
        return profilesWebClient.get()
                .uri(uri -> uri.path("/active")
                        .build())
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
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
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .toEntity(SharedProfileDto.class)
                .map(ResponseEntity::getBody)
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (getProfile {}). Cause: {}", id, throwable.toString());
                    return Mono.empty();
                });
    }
}
