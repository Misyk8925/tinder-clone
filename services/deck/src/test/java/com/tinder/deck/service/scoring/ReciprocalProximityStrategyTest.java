package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedProfileDto;
import org.junit.jupiter.api.Test;

import static com.tinder.deck.service.scoring.ScoringTestProfiles.preferences;
import static com.tinder.deck.service.scoring.ScoringTestProfiles.profile;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReciprocalProximityStrategy.
 *
 * <p>Distance is symmetric but the search radius is not: these cover the case the forward
 * strategy misses, where the viewer is willing to travel further than the candidate is.
 */
class ReciprocalProximityStrategyTest {

    /** Roughly 111 km apart along a meridian. */
    private static final double ONE_DEGREE = 1.0;

    private final ReciprocalProximityStrategy strategy = new ReciprocalProximityStrategy();

    @Test
    void scoresZeroWhenCandidateRadiusIsTooSmallToReachTheViewer() {
        // Given: the viewer accepts 500 km, the candidate only 20 km, and they are ~111 km apart
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 20), ONE_DEGREE, 0.0);

        // When / Then: the candidate can never see the viewer, so the pair scores nothing
        assertThat(strategy.calculateScore(viewer, candidate)).isZero();
    }

    @Test
    void scoresAboveZeroWhenCandidateRadiusCoversTheDistance() {
        // Given: the same distance, but the candidate accepts 500 km
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 500), ONE_DEGREE, 0.0);

        // When / Then: reachable, and never above the strategy weight
        double score = strategy.calculateScore(viewer, candidate);
        assertThat(score).isPositive().isLessThanOrEqualTo(strategy.getWeight());
    }

    @Test
    void scoresFullWeightWhenProfilesShareALocation() {
        // Given: both profiles at the same coordinates
        SharedProfileDto viewer = profile(30, preferences(18, 99, 100), 10.0, 10.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 100), 10.0, 10.0);

        // When / Then: zero distance means the full weight
        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(strategy.getWeight());
    }

    @Test
    void scoresZeroWhenEitherLocationIsMissing() {
        // Given: a candidate with no location
        SharedProfileDto viewer = profile(30, preferences(18, 99, 100), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, preferences(18, 99, 100), null, null);

        // When / Then: same convention as the forward proximity strategy
        assertThat(strategy.calculateScore(viewer, candidate)).isZero();
    }

    @Test
    void fallsBackToTheDefaultRadiusWhenCandidatePreferencesAreUnknown() {
        // Given: a candidate without preferences, ~111 km away, beyond the 100 km default
        SharedProfileDto viewer = profile(30, preferences(18, 99, 500), 0.0, 0.0);
        SharedProfileDto candidate = profile(30, null, ONE_DEGREE, 0.0);

        // When / Then: the 100 km default applies rather than a NullPointerException
        assertThat(strategy.calculateScore(viewer, candidate)).isZero();
    }
}
