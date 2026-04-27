package com.tinder.deck;

import com.tinder.deck.controller.InternalDeckController;
import com.tinder.deck.service.DeckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(InternalDeckController.class)
class InternalDeckControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DeckService deckService;

    @Test
    @DisplayName("POST /api/v1/internal/deck/ensure should return true when deck is ready")
    void shouldEnsureDeck() {
        UUID viewerId = UUID.randomUUID();
        when(deckService.ensureDeck(viewerId)).thenReturn(Mono.just(true));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/deck/ensure")
                        .queryParam("viewerId", viewerId)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    @DisplayName("POST /api/v1/internal/deck/ensure should return 500 false on error")
    void shouldReturnErrorWhenEnsureFails() {
        UUID viewerId = UUID.randomUUID();
        when(deckService.ensureDeck(viewerId)).thenReturn(Mono.error(new IllegalStateException("boom")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/internal/deck/ensure")
                        .queryParam("viewerId", viewerId)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody(Boolean.class)
                .isEqualTo(false);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public DeckService deckService() {
            return Mockito.mock(DeckService.class);
        }
    }
}
