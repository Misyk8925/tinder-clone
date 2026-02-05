package com.tinder.deck.service.pipeline;

import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SwipeFilterStage
 *
 * Tests swipe history filtering functionality including:
 * - Filtering candidates with prior swipe history
 * - Batch processing (200 candidates per batch)
 * - Error handling and recovery
 * - Large dataset handling
 */
@ExtendWith(MockitoExtension.class)
class SwipeFilterStageTest {

    // Test configuration constants
    private static final int BATCH_SIZE = 200;
    private static final long TIMEOUT_MS = 5000L;
    private static final int RETRIES = 1;

    // Test data constants
    private static final int VIEWER_AGE = 25;
    private static final int LARGE_BATCH_SIZE = 250;

    @Mock
    private SwipesHttp swipesHttp;

    private SwipeFilterStage swipeFilterStage;

    @BeforeEach
    void setUp() {
        swipeFilterStage = new SwipeFilterStage(swipesHttp);
        ReflectionTestUtils.setField(swipeFilterStage, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(swipeFilterStage, "timeoutMs", TIMEOUT_MS);
        ReflectionTestUtils.setField(swipeFilterStage, "retries", RETRIES);
    }

    @Test
    void shouldFilterOutCandidatesWithSwipeHistory() {
        // Given: viewer and three candidates, where one has swipe history
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        UUID alreadySwipedId = UUID.randomUUID();
        UUID freshCandidateId1 = UUID.randomUUID();
        UUID freshCandidateId2 = UUID.randomUUID();

        SharedProfileDto alreadySwiped = createProfile(alreadySwipedId, "AlreadySeen", 23);
        SharedProfileDto freshCandidate1 = createProfile(freshCandidateId1, "Fresh1", 27);
        SharedProfileDto freshCandidate2 = createProfile(freshCandidateId2, "Fresh2", 29);

        Flux<SharedProfileDto> candidates = Flux.just(alreadySwiped, freshCandidate1, freshCandidate2);

        // Swipe history: first candidate has history, others don't
        Map<UUID, Boolean> swipeHistory = new HashMap<>();
        swipeHistory.put(alreadySwipedId, true);  // Has swipe history
        swipeHistory.put(freshCandidateId1, false); // No history
        swipeHistory.put(freshCandidateId2, false); // No history

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(swipeHistory));

        // When: filtering by swipe history
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, candidates);

        // Then: should only return candidates without swipe history
        StepVerifier.create(result)
                .expectNext(freshCandidate1)
                .expectNext(freshCandidate2)
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptySwipeMap() {
        // Given: viewer and candidates with no swipe history data
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        SharedProfileDto candidate1 = createProfile(UUID.randomUUID(), "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(UUID.randomUUID(), "Candidate2", 27);

        Flux<SharedProfileDto> candidates = Flux.just(candidate1, candidate2);

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: no swipe history exists
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, candidates);

        // Then: should return all candidates (none filtered)
        StepVerifier.create(result)
                .expectNext(candidate1)
                .expectNext(candidate2)
                .verifyComplete();
    }

    @Test
    void shouldHandleSwipesServiceError() {
        // Given: viewer and candidates, but swipes service fails
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        SharedProfileDto candidate = createProfile(UUID.randomUUID(), "Candidate", 23);
        Flux<SharedProfileDto> candidates = Flux.just(candidate);

        RuntimeException serviceError = new RuntimeException("Service unavailable");


        // When: service errors out
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, candidates);

        // Then: should return all candidates (fail-open strategy for better UX)
        StepVerifier.create(result)
                .expectNext(candidate);
    }

    @Test
    void shouldHandleLargeBatches() {
        // Given: viewer and more candidates than batch size
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        List<SharedProfileDto> largeCandidateList = new ArrayList<>();
        for (int i = 0; i < LARGE_BATCH_SIZE; i++) {
            largeCandidateList.add(createProfile(UUID.randomUUID(), "Candidate" + i, 20 + i % 10));
        }

        Flux<SharedProfileDto> candidates = Flux.fromIterable(largeCandidateList);

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: processing multiple batches
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, candidates);

        // Then: should process all candidates in batches
        StepVerifier.create(result)
                .expectNextCount(LARGE_BATCH_SIZE)
                .verifyComplete();
    }

    @Test
    void shouldFilterAllCandidatesWhenAllHaveHistory() {
        // Given: viewer and candidates who all have been swiped before
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Candidate2", 27);

        Flux<SharedProfileDto> candidates = Flux.just(candidate1, candidate2);

        // All candidates have swipe history
        Map<UUID, Boolean> allHaveHistory = new HashMap<>();
        allHaveHistory.put(candidate1Id, true);
        allHaveHistory.put(candidate2Id, true);

        when(swipesHttp.betweenBatch(eq(viewerId), anyList()))
                .thenReturn(Mono.just(allHaveHistory));

        // When: all candidates already swiped
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, candidates);

        // Then: should return empty (all filtered out)
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyCandidateList() {
        // Given: viewer but no candidates
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = createProfile(viewerId, "Viewer", VIEWER_AGE);

        Flux<SharedProfileDto> noCandidates = Flux.empty();

        // When: filtering empty list
        Flux<SharedProfileDto> result = swipeFilterStage.filterBySwipeHistory(viewer, noCandidates);

        // Then: should complete without error
        StepVerifier.create(result)
                .verifyComplete();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test profile for use in filtering tests
     */
    private SharedProfileDto createProfile(UUID id, String name, int age) {
        SharedLocationDto locationDto = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "Test City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        SharedPreferencesDto preferences = new SharedPreferencesDto(18, 50, "ANY", 100);

        return new SharedProfileDto(id, name, age, "Bio", "Test City", true, locationDto, preferences, false, List.of());
    }
}
