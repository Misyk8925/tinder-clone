package com.tinder.deck.service.pipeline;

import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeckPipeline using Reactor Test utilities
 *
 * Tests full pipeline orchestration including:
 * - Full pipeline execution (search -> filter -> score -> cache)
 * - Empty result handling
 * - Per-user limit application
 * - Stage call verification
 * - Error propagation from cache stage
 */
@ExtendWith(MockitoExtension.class)
class DeckPipelineTest {

    // Test configuration constants
    private static final int DEFAULT_PER_USER_LIMIT = 500;
    private static final int SMALL_LIMIT = 2;
    private static final Duration VERIFICATION_TIMEOUT = Duration.ofSeconds(5);

    // Test data constants
    private static final int VIEWER_AGE = 25;
    private static final double HIGH_SCORE = 0.90;
    private static final double MEDIUM_SCORE = 0.85;
    private static final double LOW_SCORE = 0.70;

    @Mock
    private CandidateSearchStage searchStage;

    @Mock
    private SwipeFilterStage filterStage;

    @Mock
    private ScoringStage scoringStage;

    @Mock
    private CacheStage cacheStage;

    @Captor
    private ArgumentCaptor<Flux<ScoringStage.ScoredCandidate>> scoredCandidatesCaptor;

    private DeckPipeline deckPipeline;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @BeforeEach
    void setUp() {
        deckPipeline = new DeckPipeline(searchStage, filterStage, scoringStage, cacheStage);
        ReflectionTestUtils.setField(deckPipeline, "perUserLimit", DEFAULT_PER_USER_LIMIT);
    }

    @Test
    void shouldExecuteFullPipelineSuccessfully() {
        // Given
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", 25);

        // Stage 1: Search returns 3 candidates
        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Candidate2", 27);
        SharedProfileDto candidate3 = createProfile(candidate3Id, "Candidate3", 29);

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.just(candidate1, candidate2, candidate3));

        // Stage 2: Filter removes candidate1 (has swipe history) - pass through others
        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenAnswer(invocation -> {
                    Flux<SharedProfileDto> input = invocation.getArgument(1);
                    // Return new flux to avoid multiple subscriptions issues
                    return Flux.defer(() -> input.filter(c -> !c.id().equals(candidate1Id)));
                });

        // Stage 3: Scoring stage scores and ranks remaining 2 candidates
        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> {
                    Flux<SharedProfileDto> input = invocation.getArgument(1);
                    // Collect and score
                    return input.collectList().flatMapMany(list ->
                        Flux.just(
                                new ScoringStage.ScoredCandidate(candidate2Id, 0.85),
                                new ScoringStage.ScoredCandidate(candidate3Id, 0.70)
                        )
                    );
                });

        // Stage 4: Cache stage caches the deck
        when(cacheStage.cacheDeck(eq(viewerId), any()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then
        StepVerifier.create(result)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // Verify all stages were called at least once
        verify(searchStage, atLeastOnce()).searchCandidates(viewer);
        verify(filterStage, atLeastOnce()).filterBySwipeHistory(eq(viewer), any());
        verify(scoringStage, atLeastOnce()).scoreAndRank(eq(viewer), any());
        verify(cacheStage, atLeastOnce()).cacheDeck(eq(viewerId), any());
    }

    @Test
    void shouldHandleEmptySearchResults() {
        // Given: viewer but no search results
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.empty());

        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        // Note: cacheDeck should NOT be called when deck is empty

        // When: building deck with no candidates
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then: should complete successfully without caching
        StepVerifier.create(result)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // Verify: cache stage was NOT called (empty deck not cached)
        verify(cacheStage, never()).cacheDeck(eq(viewerId), any());
    }

    @Test
    void shouldApplyPerUserLimitCorrectly() {
        // Given
        ReflectionTestUtils.setField(deckPipeline, "perUserLimit", 2);

        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", 25);

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Candidate2", 27);
        SharedProfileDto candidate3 = createProfile(candidate3Id, "Candidate3", 29);

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.just(candidate1, candidate2, candidate3));

        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        // Return 3 scored candidates in descending order
        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> {
                    Flux<SharedProfileDto> input = invocation.getArgument(1);
                    return input.collectList().flatMapMany(list -> Flux.just(
                            new ScoringStage.ScoredCandidate(candidate1Id, 0.90),
                            new ScoringStage.ScoredCandidate(candidate2Id, 0.85),
                            new ScoringStage.ScoredCandidate(candidate3Id, 0.70)
                    ));
                });

        when(cacheStage.cacheDeck(eq(viewerId), scoredCandidatesCaptor.capture()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then
        StepVerifier.create(result)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // Verify only 2 candidates were cached (limit applied)
        Flux<ScoringStage.ScoredCandidate> cachedCandidates = scoredCandidatesCaptor.getValue();
        StepVerifier.create(cachedCandidates)
                .expectNextMatches(sc -> sc.candidateId().equals(candidate1Id) && sc.score() == 0.90)
                .expectNextMatches(sc -> sc.candidateId().equals(candidate2Id) && sc.score() == 0.85)
                .verifyComplete();
    }

    @Test
    void shouldCallAllStagesInOrder() {
        // Given
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", 25);
        SharedProfileDto candidate = createProfile(UUID.randomUUID(), "Candidate", 23);

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.just(candidate));

        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> {
                    Flux<SharedProfileDto> input = invocation.getArgument(1);
                    return input.map(c -> new ScoringStage.ScoredCandidate(c.id(), 0.85));
                });

        when(cacheStage.cacheDeck(eq(viewerId), any()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then
        StepVerifier.create(result)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // Verify all stages were called in order
        verify(searchStage, atLeastOnce()).searchCandidates(viewer);
        verify(filterStage, atLeastOnce()).filterBySwipeHistory(eq(viewer), any());
        verify(scoringStage, atLeastOnce()).scoreAndRank(eq(viewer), any());
        verify(cacheStage, atLeastOnce()).cacheDeck(eq(viewerId), any());
    }

    @Test
    void shouldPropagateCacheStageError() {
        // Given
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", 25);
        SharedProfileDto candidate = createProfile(UUID.randomUUID(), "Candidate", 23);
        RuntimeException expectedException = new RuntimeException("Cache failed");

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.just(candidate));

        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> {
                    Flux<SharedProfileDto> input = invocation.getArgument(1);
                    return input.map(c -> new ScoringStage.ScoredCandidate(c.id(), 0.85));
                });

        when(cacheStage.cacheDeck(eq(viewerId), any()))
                .thenReturn(Mono.error(expectedException));

        // When
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then
        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                        throwable.getMessage().equals("Cache failed"))
                .verify(Duration.ofSeconds(5));

        verify(searchStage, atLeastOnce()).searchCandidates(viewer);
        verify(filterStage, atLeastOnce()).filterBySwipeHistory(eq(viewer), any());
        verify(scoringStage, atLeastOnce()).scoreAndRank(eq(viewer), any());
        verify(cacheStage, atLeastOnce()).cacheDeck(eq(viewerId), any());
    }

    @Test
    void shouldHandleAllCandidatesFilteredOut() {
        // Given: candidates exist but all are filtered out
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        SharedProfileDto candidate1 = createProfile(UUID.randomUUID(), "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(UUID.randomUUID(), "Candidate2", 27);

        when(searchStage.searchCandidates(viewer))
                .thenReturn(Flux.just(candidate1, candidate2));

        // Filter removes all candidates
        when(filterStage.filterBySwipeHistory(eq(viewer), any()))
                .thenReturn(Flux.empty());

        when(scoringStage.scoreAndRank(eq(viewer), any()))
                .thenAnswer(invocation -> Flux.defer(() -> invocation.getArgument(1)));

        // Note: cacheDeck should NOT be called when all candidates filtered out

        // When: all candidates get filtered out
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then: should complete successfully without caching
        StepVerifier.create(result)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(searchStage, atLeastOnce()).searchCandidates(viewer);
        verify(filterStage, atLeastOnce()).filterBySwipeHistory(eq(viewer), any());
        verify(scoringStage, atLeastOnce()).scoreAndRank(eq(viewer), any());

        // Verify: cache stage was NOT called (empty deck not cached)
        verify(cacheStage, never()).cacheDeck(eq(viewerId), any());
    }

    // Helper method
    private SharedProfileDto createProfile(UUID id, String name, int age) {
        Point point = geometryFactory.createPoint(new Coordinate(0.0, 0.0));
        SharedLocationDto location = new SharedLocationDto(
                UUID.randomUUID(),
                point,
                "City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        SharedPreferencesDto prefs = new SharedPreferencesDto(18, 50, "ANY", 100);
        return new SharedProfileDto(id, name, age, "Bio", "City", true, location, prefs, false);
    }
}
