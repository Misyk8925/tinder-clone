// java
    package com.tinder.deck;

    import com.tinder.deck.controlller.DeckController;
    import com.tinder.deck.adapters.ProfilesHttp;
    import com.tinder.deck.service.DeckCache;
    import com.tinder.deck.service.DeckScheduler;
    import com.tinder.deck.service.DeckService;
    import org.junit.jupiter.api.BeforeEach;
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

    import static org.mockito.ArgumentMatchers.any;
    import static org.mockito.Mockito.when;

    /*
     * Tests for DeckController admin endpoints
     */
    @WebFluxTest(DeckController.class)
    class DeckControllerTest {

        @Autowired
        private WebTestClient webTestClient;

        // Inject mocked beans provided by TestConfig
        @Autowired
        private DeckCache deckCache;

        @Autowired
        private DeckService deckService;

        private UUID testViewerId;

        @BeforeEach
        void setUp() {
            testViewerId = UUID.randomUUID();
        }

        @Test
        @DisplayName("GET /api/v1/admin/deck/exists should return true when deck exists")
        void testExistsEndpointWhenDeckExists() {
            // Given: Deck exists (size > 0)
            when(deckCache.size(testViewerId)).thenReturn(Mono.just(10L));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck/exists")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("GET /api/v1/admin/deck/exists should return false when deck does not exist")
        void testExistsEndpointWhenDeckDoesNotExist() {
            // Given: Deck does not exist (size = 0)
            when(deckCache.size(testViewerId)).thenReturn(Mono.just(0L));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck/exists")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class)
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("GET /api/v1/admin/deck/size should return deck size")
        void testSizeEndpoint() {
            // Given: Deck has 42 candidates
            when(deckCache.size(testViewerId)).thenReturn(Mono.just(42L));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck/size")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Long.class)
                    .isEqualTo(42L);
        }

        @Test
        @DisplayName("GET /api/v1/admin/deck/size should return 0 for non-existent deck")
        void testSizeEndpointForNonExistentDeck() {
            // Given: Deck does not exist
            when(deckCache.size(testViewerId)).thenReturn(Mono.just(0L));

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck/size")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Long.class)
                    .isEqualTo(0L);
        }

        @Test
        @DisplayName("POST /api/v1/admin/deck/rebuild should return not implemented")
        void testRebuildEndpoint() {
            // When & Then
            webTestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck/rebuild")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectBody(String.class);
        }

        @Test
        @DisplayName("DELETE /api/v1/admin/deck should invalidate deck")
        void testInvalidateEndpoint() {
            // Given: Invalidate returns 2 (deck + timestamp keys deleted)
            when(deckCache.invalidate(testViewerId)).thenReturn(Mono.just(2L));

            // When & Then
            webTestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("Deleted 2 keys");
        }

        @Test
        @DisplayName("DELETE /api/v1/admin/deck should handle non-existent deck")
        void testInvalidateEndpointForNonExistentDeck() {
            // Given: Invalidate returns 0 (no keys deleted)
            when(deckCache.invalidate(testViewerId)).thenReturn(Mono.just(0L));

            // When & Then
            webTestClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/admin/deck")
                            .queryParam("viewerId", testViewerId.toString())
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .isEqualTo("Deleted 0 keys");
        }

        @TestConfiguration
        static class TestConfig {
            @Bean
            public DeckCache deckCache() {
                // Return a Mockito mock as a test bean
                return Mockito.mock(DeckCache.class);
            }

            @Bean
            public DeckService deckService() {
                return Mockito.mock(DeckService.class);
            }

            @Bean
            public DeckScheduler deckScheduler() {
                // Provide missing scheduler dependency if controller requires it
                return Mockito.mock(DeckScheduler.class);
            }

            @Bean
            public ProfilesHttp profilesHttp() {
                // Provide missing adapter dependency if controller requires it
                return Mockito.mock(ProfilesHttp.class);
            }
        }
    }