package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedProfileDto;
import org.junit.jupiter.api.Test;

import com.tinder.deck.service.popularity.LikeRates;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.tinder.deck.service.scoring.ScoringTestProfiles.preferences;
import static com.tinder.deck.service.scoring.ScoringTestProfiles.profile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PopularityStrategy.
 *
 * <p>The load-bearing behaviour is that the like rate multiplies the two preference fits
 * rather than adding to them: a match needs both sides, and a wildly popular profile far
 * outside the viewer's radius is worth nothing.
 */
class PopularityStrategyTest {

    private static final double BASE_RATE = 0.125;

    private final PopularityStrategy strategy = new PopularityStrategy();

    private static ScoringContext context(SharedProfileDto candidate, double rate) {
        return new ScoringContext(new LikeRates(Map.of(candidate.id(), rate), Set.of(), BASE_RATE));
    }

    @Test
    void ranksTheMoreLikedOfTwoEquallySuitableCandidatesHigher() {
        // Given: two candidates identical in age and location, differing only in like rate
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto liked = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto ignored = profile(30, preferences(18, 99, 500), 0.0, 0.0);

        double likedScore = strategy.calculateScore(viewer, liked, context(liked, 0.4));
        double ignoredScore = strategy.calculateScore(viewer, ignored, context(ignored, 0.02));

        assertThat(likedScore).isGreaterThan(ignoredScore);
    }

    @Test
    void scoresZeroForACandidateBeyondTheViewerRadiusHoweverPopular() {
        // Given: a very well liked candidate about 111 km away, and a viewer who travels 20
        SharedProfileDto viewer = profile(30, preferences(18, 99, 20), 0.0, 0.0);
        SharedProfileDto distant = profile(30, preferences(18, 99, 500), 1.0, 0.0);

        // Then: popularity multiplies the fits, it does not add to them
        assertThat(strategy.calculateScore(viewer, distant, context(distant, 0.9))).isZero();
    }

    @Test
    void scoresZeroWhenTheCandidateCouldNeverReachTheViewer() {
        // Given: the viewer travels far, the candidate barely at all
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto unreachable = profile(30, preferences(18, 99, 1), 1.0, 0.0);

        // Then: a match needs both sides, so the mirror fit gates it too
        assertThat(strategy.calculateScore(viewer, unreachable, context(unreachable, 0.9))).isZero();
    }

    @Test
    void fallsBackToTheBaseRateForACandidateNobodyHasSeen() {
        // Given: a candidate absent from the loaded rates
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto newcomer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        ScoringContext empty = ScoringContext.empty(BASE_RATE);

        // Then: ranked low rather than last — zero would bury a new profile forever
        assertThat(strategy.calculateScore(viewer, newcomer, empty)).isPositive();
    }

    @Test
    void contributesNothingWithoutALoadedContext() {
        // Given: the two-argument form, which has no rates to rank by
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 500), 0.0, 0.0);

        // Then: the variant degrades to its preference terms rather than inventing a rate
        assertThat(strategy.calculateScore(viewer, candidate)).isZero();
    }

    @Test
    void scoresNeutrallyWhenAProfileHasNoPreferencesOrAge() {
        SharedProfileDto viewer = profile(30, null, 0.0, 0.0);
        SharedProfileDto candidate = profile(null, preferences(18, 99, 500), 0.0, 0.0);

        // Neither NaN nor an exception: unknown preferences are neutral, as in the older
        // strategies.
        double score = strategy.calculateScore(viewer, candidate, context(candidate, 0.3));
        assertThat(score).isFinite().isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void treatsAnUnknownIdAsUnseenRatherThanFailing() {
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        ScoringContext other = new ScoringContext(
                new LikeRates(Map.of(UUID.randomUUID(), 0.9), Set.of(), BASE_RATE));

        assertThat(strategy.calculateScore(viewer, candidate, other)).isPositive();
    }
}
