package com.tinder.profiles.deck;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckClient {

    private static final long ENSURE_FAILURE_WARN_INTERVAL_MS = 30_000L;

    private final WebClient.Builder webClientBuilder;
    private final AtomicLong lastEnsureFailureWarnAt = new AtomicLong();

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
            logEnsureFailure(viewerId, error);
            return false;
        }
    }

    private void logEnsureFailure(UUID viewerId, RuntimeException error) {
        long now = System.currentTimeMillis();
        long previous = lastEnsureFailureWarnAt.get();
        if (now - previous >= ENSURE_FAILURE_WARN_INTERVAL_MS
                && lastEnsureFailureWarnAt.compareAndSet(previous, now)) {
            log.warn("Deck service ensure failed; suppressing repeated failures for {} ms. Last viewer {}: {}",
                    ENSURE_FAILURE_WARN_INTERVAL_MS, viewerId, error.getMessage());
            return;
        }
        log.debug("Deck service ensure failed for viewer {}: {}", viewerId, error.getMessage());
    }
}
