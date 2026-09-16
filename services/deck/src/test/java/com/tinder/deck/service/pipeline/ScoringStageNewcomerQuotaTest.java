package com.tinder.deck.service.pipeline;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.service.ScoringService;
import com.tinder.deck.service.popularity.LikeRateStore;
import com.tinder.deck.service.popularity.LikeRates;
import com.tinder.deck.service.popularity.PopularityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the newcomer quota in ScoringStage.
 *
 * <p>The quota exists because a ranker trained on its own output starves anything it has
 * never shown: not shown means not swiped on, which means no evidence, which means never
 * shown. These pin the two properties that make it work — the reserved slots sit where a
 * viewer actually reaches them, and only variants that rank on learned rates pay for it.
 */
@ExtendWith(MockitoExtension.class)
class ScoringStageNewcomerQuotaTest {

    private static final String LEARNED_VARIANT = "popularity";
    private static final double BASE_RATE = 0.125;

    @Mock
    private ScoringService scoringService;

    @Mock
    private LikeRateStore likeRateStore;

    private final PopularityProperties properties = new PopularityProperties();
    private ScoringStage stage;

    @BeforeEach
    void setUp() {
        stage = new ScoringStage(scoringService, likeRateStore, properties);
        ReflectionTestUtils.setField(stage, "parallelism", 4);
        lenient().when(likeRateStore.baseRate()).thenReturn(BASE_RATE);
    }

    @Test
    void neverTouchesRedisForAVariantThatDoesNotRankOnRates() {
        // Given: the baseline variant, which knows nothing about like rates
        when(scoringService.variantFor(any())).thenReturn("baseline");
        when(scoringService.variantUsesLikeRates("baseline")).thenReturn(false);
        when(scoringService.score(any(), any(), any(), any())).thenReturn(1.0);
        SharedProfileDto viewer = profile();

        stage.scoreAndRank(viewer, Flux.fromIterable(pool(20))).collectList().block();

        // Then: existing rankings pay nothing for this feature — not even a lookup
        verify(likeRateStore, never()).ratesFor(any());
    }

    @Test
    void placesUnseenProfilesWhereAViewerActuallyReachesThem() {
        // Given: a hundred candidates of which the last twenty have never been shown, and a
        // ranking that would put every one of those unseen ones at the very bottom
        List<SharedProfileDto> pool = pool(100);
        Set<UUID> unseen = new HashSet<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            scores.put(pool.get(i).id(), (double) (pool.size() - i));
            if (i >= 80) {
                unseen.add(pool.get(i).id());
            }
        }
        learnedVariantWith(pool, scores, unseen);

        List<ScoringStage.ScoredCandidate> deck =
                stage.scoreAndRank(profile(), Flux.fromIterable(pool)).collectList().block();

        // Then: with a 12% quota one slot in roughly eight is reserved, so an unseen profile
        // appears well inside the first cards a viewer sees rather than at position 80
        assertThat(deck).isNotNull();
        List<UUID> firstTwenty = deck.subList(0, 20).stream()
                .map(ScoringStage.ScoredCandidate::candidateId).toList();
        assertThat(firstTwenty).anyMatch(unseen::contains);
    }

    @Test
    void keepsTheDeckOrderedByScoreAfterPromotingNewcomers() {
        List<SharedProfileDto> pool = pool(60);
        Set<UUID> unseen = new HashSet<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            scores.put(pool.get(i).id(), (double) (pool.size() - i));
            if (i >= 50) {
                unseen.add(pool.get(i).id());
            }
        }
        learnedVariantWith(pool, scores, unseen);

        List<ScoringStage.ScoredCandidate> deck =
                stage.scoreAndRank(profile(), Flux.fromIterable(pool)).collectList().block();

        // The deck is written to a Redis sorted set, so a promoted profile that kept its old
        // score would simply sink back to where it was. Position has to be expressed in score.
        assertThat(deck).isNotNull();
        for (int i = 1; i < deck.size(); i++) {
            assertThat(deck.get(i).score()).isLessThanOrEqualTo(deck.get(i - 1).score());
        }
    }

    @Test
    void loosesNoCandidateWhilePromoting() {
        List<SharedProfileDto> pool = pool(40);
        Set<UUID> unseen = new HashSet<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            scores.put(pool.get(i).id(), (double) (pool.size() - i));
            if (i >= 30) {
                unseen.add(pool.get(i).id());
            }
        }
        learnedVariantWith(pool, scores, unseen);

        List<ScoringStage.ScoredCandidate> deck =
                stage.scoreAndRank(profile(), Flux.fromIterable(pool)).collectList().block();

        assertThat(deck).isNotNull().hasSize(pool.size());
        assertThat(deck).extracting(ScoringStage.ScoredCandidate::candidateId)
                .containsExactlyInAnyOrderElementsOf(pool.stream().map(SharedProfileDto::id).toList());
    }

    @Test
    void changesNothingWhenEveryCandidateHasAlreadyBeenShown() {
        List<SharedProfileDto> pool = pool(30);
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            scores.put(pool.get(i).id(), (double) (pool.size() - i));
        }
        learnedVariantWith(pool, scores, Set.of());

        List<ScoringStage.ScoredCandidate> deck =
                stage.scoreAndRank(profile(), Flux.fromIterable(pool)).collectList().block();

        assertThat(deck).isNotNull();
        assertThat(deck.get(0).candidateId()).isEqualTo(pool.get(0).id());
        assertThat(deck.get(0).score()).isEqualTo(scores.get(pool.get(0).id()));
    }

    @Test
    void reservesNothingWhenTheQuotaIsZero() {
        properties.setNewcomerQuota(0);
        List<SharedProfileDto> pool = pool(30);
        Set<UUID> unseen = new HashSet<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (int i = 0; i < pool.size(); i++) {
            scores.put(pool.get(i).id(), (double) (pool.size() - i));
            if (i >= 25) {
                unseen.add(pool.get(i).id());
            }
        }
        learnedVariantWith(pool, scores, unseen);

        List<ScoringStage.ScoredCandidate> deck =
                stage.scoreAndRank(profile(), Flux.fromIterable(pool)).collectList().block();

        assertThat(deck).isNotNull();
        assertThat(unseen).doesNotContain(deck.get(0).candidateId());
        assertThat(deck.get(0).score()).isEqualTo(scores.get(pool.get(0).id()));
    }

    private void learnedVariantWith(List<SharedProfileDto> pool, Map<UUID, Double> scores, Set<UUID> unseen) {
        when(scoringService.variantFor(any())).thenReturn(LEARNED_VARIANT);
        when(scoringService.variantUsesLikeRates(LEARNED_VARIANT)).thenReturn(true);
        when(likeRateStore.ratesFor(any()))
                .thenReturn(Mono.just(new LikeRates(Map.of(), unseen, BASE_RATE)));
        when(scoringService.score(any(), any(), any(), any()))
                .thenAnswer(call -> scores.getOrDefault(((SharedProfileDto) call.getArgument(1)).id(), 0.0));
    }

    private static List<SharedProfileDto> pool(int size) {
        List<SharedProfileDto> pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            pool.add(profile());
        }
        return pool;
    }

    private static SharedProfileDto profile() {
        SharedLocationDto location = new SharedLocationDto(UUID.randomUUID(), 0.0, 0.0, "City",
                LocalDateTime.now(), LocalDateTime.now());
        return new SharedProfileDto(UUID.randomUUID(), "Test", 30, "Bio", "City", true, location,
                new SharedPreferencesDto(18, 99, "ANY", 100), false, List.of(), List.of());
    }
}
