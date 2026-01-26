package com.tinder.deck.service.pipeline;

import com.tinder.deck.service.DeckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CacheStage
 *
 * Tests deck caching functionality including:
 * - Successful caching with TTL
 * - Score order preservation
 * - Error propagation from cache
 * - Large deck handling
 */
@ExtendWith(MockitoExtension.class)
class CacheStageTest {

    // Test configuration constants
    private static final long TTL_MINUTES = 60;
    private static final long CUSTOM_TTL_MINUTES = 120;

    // Test data constants
    private static final double HIGH_SCORE = 0.95;
    private static final double MEDIUM_SCORE = 0.85;
    private static final double LOW_SCORE = 0.65;
    private static final int LARGE_DECK_SIZE = 500;

    @Mock
    private DeckCache deckCache;

    private CacheStage cacheStage;

    @Captor
    private ArgumentCaptor<List<Map.Entry<UUID, Double>>> deckCaptor;

    @Captor
    private ArgumentCaptor<Duration> durationCaptor;

    @BeforeEach
    void setUp() {
        cacheStage = new CacheStage(deckCache);
        ReflectionTestUtils.setField(cacheStage, "ttlMinutes", TTL_MINUTES);
    }

    @Test
    void shouldCacheDeckSuccessfully() {
        // Given
        UUID viewerId = UUID.randomUUID();
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        ScoringStage.ScoredCandidate scored1 = new ScoringStage.ScoredCandidate(candidate1Id, 0.95);
        ScoringStage.ScoredCandidate scored2 = new ScoringStage.ScoredCandidate(candidate2Id, 0.80);
        ScoringStage.ScoredCandidate scored3 = new ScoringStage.ScoredCandidate(candidate3Id, 0.65);

        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(scored1, scored2, scored3);

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), durationCaptor.capture());

        List<Map.Entry<UUID, Double>> capturedDeck = deckCaptor.getValue();
        assertThat(capturedDeck).hasSize(3);
        assertThat(capturedDeck.get(0).getKey()).isEqualTo(candidate1Id);
        assertThat(capturedDeck.get(0).getValue()).isEqualTo(0.95);
        assertThat(capturedDeck.get(1).getKey()).isEqualTo(candidate2Id);
        assertThat(capturedDeck.get(1).getValue()).isEqualTo(0.80);
        assertThat(capturedDeck.get(2).getKey()).isEqualTo(candidate3Id);
        assertThat(capturedDeck.get(2).getValue()).isEqualTo(0.65);

        Duration capturedDuration = durationCaptor.getValue();
        assertThat(capturedDuration).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void shouldHandleEmptyDeck() {
        // Given
        UUID viewerId = UUID.randomUUID();
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.empty();

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then: should complete without calling writeDeck
        StepVerifier.create(result)
                .verifyComplete();

        // Verify: writeDeck was NOT called for empty deck
        verify(deckCache, never()).writeDeck(any(), anyList(), any(Duration.class));
    }

    @Test
    void shouldHandleSingleCandidate() {
        // Given
        UUID viewerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        ScoringStage.ScoredCandidate scored = new ScoringStage.ScoredCandidate(candidateId, 0.85);
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(scored);

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> capturedDeck = deckCaptor.getValue();
        assertThat(capturedDeck).hasSize(1);
        assertThat(capturedDeck.get(0).getKey()).isEqualTo(candidateId);
        assertThat(capturedDeck.get(0).getValue()).isEqualTo(0.85);
    }

    @Test
    void shouldPropagateErrorFromCache() {
        // Given
        UUID viewerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        ScoringStage.ScoredCandidate scored = new ScoringStage.ScoredCandidate(candidateId, 0.85);
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(scored);

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleLargeDeck() {
        // Given
        UUID viewerId = UUID.randomUUID();

        // Create 500 scored candidates
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.range(0, 500)
                .map(i -> new ScoringStage.ScoredCandidate(UUID.randomUUID(), 1.0 - (i * 0.001)));

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> capturedDeck = deckCaptor.getValue();
        assertThat(capturedDeck).hasSize(500);
    }

    @Test
    void shouldUseConfiguredTTL() {
        // Given
        ReflectionTestUtils.setField(cacheStage, "ttlMinutes", 120L);

        UUID viewerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        ScoringStage.ScoredCandidate scored = new ScoringStage.ScoredCandidate(candidateId, 0.85);
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(scored);

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(deckCache).writeDeck(eq(viewerId), anyList(), durationCaptor.capture());

        Duration capturedDuration = durationCaptor.getValue();
        assertThat(capturedDuration).isEqualTo(Duration.ofMinutes(120));
    }

    @Test
    void shouldPreserveScoreOrder() {
        // Given
        UUID viewerId = UUID.randomUUID();
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        // Scores in descending order
        ScoringStage.ScoredCandidate scored1 = new ScoringStage.ScoredCandidate(candidate1Id, 0.90);
        ScoringStage.ScoredCandidate scored2 = new ScoringStage.ScoredCandidate(candidate2Id, 0.75);
        ScoringStage.ScoredCandidate scored3 = new ScoringStage.ScoredCandidate(candidate3Id, 0.60);

        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(scored1, scored2, scored3);

        when(deckCache.writeDeck(eq(viewerId), anyList(), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheStage.cacheDeck(viewerId, scoredCandidates);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(deckCache).writeDeck(eq(viewerId), deckCaptor.capture(), any(Duration.class));

        List<Map.Entry<UUID, Double>> capturedDeck = deckCaptor.getValue();
        assertThat(capturedDeck).hasSize(3);
        // Verify order is preserved
        assertThat(capturedDeck.get(0).getValue()).isEqualTo(0.90);
        assertThat(capturedDeck.get(1).getValue()).isEqualTo(0.75);
        assertThat(capturedDeck.get(2).getValue()).isEqualTo(0.60);
    }
}
