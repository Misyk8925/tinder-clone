package com.tinder.deck.adapters;

import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfilesHttp {
    private static final Logger log = LoggerFactory.getLogger(ProfilesHttp.class);
    private final WebClient profilesWebClient;

     public Flux<SharedProfileDto> searchProfiles(UUID viewerId, SharedPreferencesDto preferences, int limit) {
         return profilesWebClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("viewerId", viewerId)
                        .queryParam("gender", preferences.gender())
                        .queryParam("minAge", preferences.minAge())
                        .queryParam("maxAge", preferences.maxAge())
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToFlux(SharedProfileDto.class)
                .onErrorResume(throwable -> {
                    // Log and return empty stream on connection errors or other failures
                    log.warn("Failed to call profiles service (searchProfiles). Returning empty result. Cause: {}", throwable.toString());
                    return Flux.empty();
                });
     }

     public Flux<SharedProfileDto> fetchPage(int page, int limit) {

         return profilesWebClient.get()
                 .uri(uri -> uri.path("/page")
                        .queryParam("page", page)
                        .queryParam("limit", limit)
                        .build())
                 .retrieve()
                 .bodyToFlux(SharedProfileDto.class)
                 .onErrorResume(throwable -> {
                     log.warn("Failed to call profiles service (fetchPage). Returning empty page. Cause: {}", throwable.toString());
                     return Flux.empty();
                 });
     }

    /**
     * Получить всех активных пользователей
     * Можно заменить endpoint на реальный, если потребуется
     */
    public Flux<SharedProfileDto> getActiveUsers() {
        // Временная реализация: получаем первую страницу с большим лимитом
        return profilesWebClient.get()
                .uri(uri -> uri.path("/active")
                        .build())
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
                .retrieve()
                .bodyToMono(SharedProfileDto.class)
                .onErrorResume(throwable -> {
                    log.warn("Failed to call profiles service (getProfile {}). Cause: {}", id, throwable.toString());
                    return Mono.empty();
                });
    }

}
