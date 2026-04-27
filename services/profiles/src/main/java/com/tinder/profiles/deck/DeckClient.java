package com.tinder.profiles.deck;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${deck.base-url:${DECK_SERVICE_URL:http://localhost:8030}}")
    private String deckBaseUrl;

    public boolean ensureDeck(UUID viewerId) {
        try {
            Boolean response = webClientBuilder
                    .baseUrl(deckBaseUrl)
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/internal/deck/ensure")
                            .queryParam("viewerId", viewerId)
                            .build())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            return Boolean.TRUE.equals(response);
        } catch (WebClientResponseException | WebClientRequestException error) {
            log.warn("Deck service ensure failed for viewer {}: {}", viewerId, error.getMessage());
            return false;
        }
    }
}
