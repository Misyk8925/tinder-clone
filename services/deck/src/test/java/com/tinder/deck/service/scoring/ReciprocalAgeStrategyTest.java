package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedProfileDto;
import org.junit.jupiter.api.Test;

import static com.tinder.deck.service.scoring.ScoringTestProfiles.preferences;
import static com.tinder.deck.service.scoring.ScoringTestProfiles.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for ReciprocalAgeStrategy.
 *
 * <p>Covers the mirrored age curve, the neutral score when the candidate's preferences or
 * the viewer's age are unknown, and the linear decay outside the candidate's range.
 */
class ReciprocalAgeStrategyTest {

    private final ReciprocalAgeStrategy strategy = new ReciprocalAgeStrategy();

    @Test
    void scoresFullWeightWhenViewerAgeIsInsideCandidateRange() {
        // Given: a 30 year old viewer and a candidate who wants 25..35
        SharedProfileDto viewer = profile(30, preferences(18, 99, 100));
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100));

        // When / Then: the viewer is exactly what the candidate asked for
        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(strategy.getWeight());
    }

    @Test
    void decaysLinearlyWhenViewerIsOlderThanCandidateWants() {
        // Given: a 40 year old viewer and a candidate who wants at most 35
        SharedProfileDto viewer = profile(40, preferences(18, 99, 100));
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100));

        // When / Then: five years past the range costs half the weight
        assertThat(strategy.calculateScore(viewer, candidate))
                .isCloseTo(0.5 * strategy.getWeight(), within(1e-9));
    }

    @Test
    void scoresZeroWhenViewerIsFarOutsideCandidateRange() {
        // Given: a viewer 15 years past what the candidate wants
        SharedProfileDto viewer = profile(50, preferences(18, 99, 100));
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100));

        // When / Then: the decay floor is zero, never negative
        assertThat(strategy.calculateScore(viewer, candidate)).isZero();
    }

    @Test
    void scoresNeutralWhenCandidatePreferencesAreUnknown() {
        // Given: a candidate without preferences
        SharedProfileDto viewer = profile(30, preferences(18, 99, 100));
        SharedProfileDto candidate = profile(28, null);

        // When / Then: reciprocity cannot be judged, so the score is neutral
        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(0.5);
    }

    @Test
    void scoresNeutralWhenViewerAgeIsMissing() {
        // Given: a viewer whose age was never set
        SharedProfileDto viewer = profile(null, preferences(18, 99, 100));
        SharedProfileDto candidate = profile(28, preferences(25, 35, 100));

        // When / Then: neutral rather than a NullPointerException
        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(0.5);
    }
}
