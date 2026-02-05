package com.tinder.deck.service.pipeline;

import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.ScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ScoringStage
 *
 * Tests candidate scoring and ranking functionality including:
 * - Score calculation and descending sort
 * - Parallel processing of candidates
 * - Edge cases (zero, negative, tied scores)
 * - Large dataset handling
 */
@ExtendWith(MockitoExtension.class)
class ScoringStageTest {

    // Test configuration constants
    private static final int PARALLELISM = 8;

    // Test score constants for readability
    private static final double HIGH_SCORE = 0.95;
    private static final double MEDIUM_SCORE = 0.80;
    private static final double LOW_SCORE = 0.60;
    private static final double TIED_SCORE = 0.75;
    private static final double ZERO_SCORE = 0.0;
    private static final double NEGATIVE_SCORE = -0.5;
    private static final double POSITIVE_SCORE = 0.3;

    // Test data constants
    private static final int VIEWER_AGE = 25;
    private static final int LARGE_CANDIDATE_COUNT = 100;

    @Mock
    private ScoringService scoringService;

    private ScoringStage scoringStage;

    @BeforeEach
    void setUp() {
        scoringStage = new ScoringStage(scoringService);
        ReflectionTestUtils.setField(scoringStage, "parallelism", PARALLELISM);
    }

    @Test
    void shouldScoreAndRankCandidatesInDescendingOrder() {
        // Given: viewer and three candidates with different compatibility scores
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        UUID lowScoreCandidateId = UUID.randomUUID();
        UUID highScoreCandidateId = UUID.randomUUID();
        UUID mediumScoreCandidateId = UUID.randomUUID();

        SharedProfileDto lowScoreCandidate = createProfile(lowScoreCandidateId, "LowMatch", 23);
        SharedProfileDto highScoreCandidate = createProfile(highScoreCandidateId, "HighMatch", 27);
        SharedProfileDto mediumScoreCandidate = createProfile(mediumScoreCandidateId, "MediumMatch", 29);

        Flux<SharedProfileDto> candidates = Flux.just(lowScoreCandidate, highScoreCandidate, mediumScoreCandidate);

        // Mock different compatibility scores
        when(scoringService.score(viewer, lowScoreCandidate)).thenReturn(LOW_SCORE);
        when(scoringService.score(viewer, highScoreCandidate)).thenReturn(HIGH_SCORE);
        when(scoringService.score(viewer, mediumScoreCandidate)).thenReturn(MEDIUM_SCORE);

        // When: scoring and ranking
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, candidates);

        // Then: should return in descending score order (high -> medium -> low)
        StepVerifier.create(result)
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(highScoreCandidateId);
                    assertThat(scored.score()).isEqualTo(HIGH_SCORE);
                })
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(mediumScoreCandidateId);
                    assertThat(scored.score()).isEqualTo(MEDIUM_SCORE);
                })
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(lowScoreCandidateId);
                    assertThat(scored.score()).isEqualTo(LOW_SCORE);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyCandidateList() {
        // Given: viewer but no candidates to score
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);
        Flux<SharedProfileDto> noCandidates = Flux.empty();

        // When: scoring empty list
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, noCandidates);

        // Then: should complete without error
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldHandleSingleCandidate() {
        // Given: viewer and exactly one candidate
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);
        UUID candidateId = UUID.randomUUID();
        SharedProfileDto candidate = createProfile(candidateId, "OnlyOne", 23);

        Flux<SharedProfileDto> candidates = Flux.just(candidate);

        when(scoringService.score(viewer, candidate)).thenReturn(MEDIUM_SCORE);

        // When: scoring single candidate
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, candidates);

        // Then: should return that one candidate with score
        StepVerifier.create(result)
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(candidateId);
                    assertThat(scored.score()).isEqualTo(MEDIUM_SCORE);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleTiedScores() {
        // Given: viewer and two candidates with identical scores
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        SharedProfileDto candidate1 = createProfile(candidate1Id, "Candidate1", 23);
        SharedProfileDto candidate2 = createProfile(candidate2Id, "Candidate2", 27);

        Flux<SharedProfileDto> candidates = Flux.just(candidate1, candidate2);

        when(scoringService.score(viewer, candidate1)).thenReturn(TIED_SCORE);
        when(scoringService.score(viewer, candidate2)).thenReturn(TIED_SCORE);

        // When: both candidates have same score
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, candidates);

        // Then: both should be returned (order may vary for ties)
        StepVerifier.create(result)
                .assertNext(scored -> assertThat(scored.score()).isEqualTo(TIED_SCORE))
                .assertNext(scored -> assertThat(scored.score()).isEqualTo(TIED_SCORE))
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroScores() {
        // Given: viewer and candidate with zero compatibility
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        UUID candidateId = UUID.randomUUID();
        SharedProfileDto candidate = createProfile(candidateId, "NoMatch", 23);

        Flux<SharedProfileDto> candidates = Flux.just(candidate);

        when(scoringService.score(viewer, candidate)).thenReturn(ZERO_SCORE);

        // When: candidate scores zero
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, candidates);

        // Then: should still be returned with zero score
        StepVerifier.create(result)
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(candidateId);
                    assertThat(scored.score()).isEqualTo(ZERO_SCORE);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNegativeScores() {
        // Given: viewer and candidates with positive and negative scores
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        UUID poorMatchId = UUID.randomUUID();
        UUID okayMatchId = UUID.randomUUID();

        SharedProfileDto poorMatch = createProfile(poorMatchId, "PoorMatch", 23);
        SharedProfileDto okayMatch = createProfile(okayMatchId, "OkayMatch", 27);

        Flux<SharedProfileDto> candidates = Flux.just(poorMatch, okayMatch);

        when(scoringService.score(viewer, poorMatch)).thenReturn(NEGATIVE_SCORE);
        when(scoringService.score(viewer, okayMatch)).thenReturn(POSITIVE_SCORE);

        // When: one negative, one positive score
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, candidates);

        // Then: positive score should rank higher
        StepVerifier.create(result)
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(okayMatchId);
                    assertThat(scored.score()).isEqualTo(POSITIVE_SCORE);
                })
                .assertNext(scored -> {
                    assertThat(scored.candidateId()).isEqualTo(poorMatchId);
                    assertThat(scored.score()).isEqualTo(NEGATIVE_SCORE);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleLargeNumberOfCandidates() {
        // Given: viewer and many candidates (tests parallel processing)
        SharedProfileDto viewer = createProfile(UUID.randomUUID(), "Viewer", VIEWER_AGE);

        Flux<SharedProfileDto> manyCandidates = Flux.range(0, LARGE_CANDIDATE_COUNT)
                .map(i -> createProfile(UUID.randomUUID(), "Candidate" + i, 20 + i % 30));

        // Mock random scores for all candidates
        when(scoringService.score(any(SharedProfileDto.class), any(SharedProfileDto.class)))
                .thenAnswer(invocation -> Math.random());

        // When: processing large dataset
        Flux<ScoringStage.ScoredCandidate> result = scoringStage.scoreAndRank(viewer, manyCandidates);

        // Then: should process all candidates
        StepVerifier.create(result)
                .expectNextCount(LARGE_CANDIDATE_COUNT)
                .verifyComplete();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test profile for use in scoring tests
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
