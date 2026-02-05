package com.tinder.deck.adapters;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.resilience.DeckResilience;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilesHttp {
    private static final Logger log = LoggerFactory.getLogger(ProfilesHttp.class);
    private final WebClient profilesWebClient;
    private final DeckResilience resilience;

    public Flux<SharedProfileDto> searchProfiles(UUID viewerId, SharedPreferencesDto preferences, int limit) {
        // Use default preferences if null
        if (preferences == null) {
            log.warn("Preferences is null for viewer {}, using defaults", viewerId);
            preferences = new SharedPreferencesDto(18, 50, "ANY", 100);
        }

        log.debug("Calling profiles service /search with viewerId={}, gender={}, minAge={}, maxAge={}, maxRange={}, limit={}",
                viewerId, preferences.gender(), preferences.minAge(), preferences.maxAge(), preferences.maxRange(), limit);

        final SharedPreferencesDto finalPrefs = preferences;
        Flux<SharedProfileDto> call = profilesWebClient.get()
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
                .bodyToFlux(SharedProfileDto.class);

        return resilience.protectProfiles(call)
                .doFinally(st -> {
                    if (st == SignalType.CANCEL) {
                        log.debug("Profiles search cancelled for viewer {}", viewerId);
                    }
                })
                .onErrorResume(throwable -> {
                    log.warn("Profiles search failed (viewerId={}) -> empty result. Cause: {}", viewerId, throwable.toString());
                    return Flux.empty();
                });
    }


    public Flux<SharedProfileDto> getActiveUsers() {
        Flux<SharedProfileDto> call = profilesWebClient.get()
                .uri(uri -> uri.path("/active")
                        .build())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class);

        return resilience.protectProfiles(call)
                .onErrorResume(throwable -> {
                    log.warn("Profiles getActiveUsers failed -> empty result. Cause: {}", throwable.toString());
                    return Flux.empty();
                });
    }

    /**
     * Fetch a single profile by id from Profiles service
     */
    public Mono<SharedProfileDto> getProfile(UUID id) {
        Mono<SharedProfileDto> call = profilesWebClient.get()
                .uri("/{id}", id)
                .retrieve()
                .toEntity(SharedProfileDto.class)
                .map(ResponseEntity::getBody);

        return resilience.protectProfiles(call)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                .doOnError(throwable -> log.warn("Profiles getProfile {} failed. Cause: {}", id, throwable.toString()));
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

        Flux<SharedProfileDto> call = profilesWebClient.get()
                .uri(uri -> uri.path("/by-ids")
                        .queryParam("ids", idsParam)
                        .build())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class);

        return resilience.protectProfiles(call)
                .doOnError(throwable -> log.warn("Profiles getProfilesByIds failed (size={}). Cause: {}", profileIds.size(), throwable.toString()));
    }
}
