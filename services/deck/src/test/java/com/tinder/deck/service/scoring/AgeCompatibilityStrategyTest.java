package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.junit.jupiter.api.Test;

import static com.tinder.deck.service.scoring.ScoringTestProfiles.preferences;
import static com.tinder.deck.service.scoring.ScoringTestProfiles.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the forward strategies, pinning the age and proximity curves that
 * {@code baseline} must keep reproducing, plus the two missing-value regressions found
 * while adding their reciprocal mirrors.
 */
class AgeCompatibilityStrategyTest {

    private final AgeCompatibilityStrategy strategy = new AgeCompatibilityStrategy();

    @Test
    void scoresFullWeightInsideThePreferredRange() {
        SharedProfileDto viewer = profile(30, preferences(25, 35, 100));
        SharedProfileDto candidate = profile(28, preferences(18, 99, 100));

        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(strategy.getWeight());
    }

    @Test
    void decaysLinearlyBelowThePreferredRange() {
        SharedProfileDto viewer = profile(30, preferences(25, 35, 100));
        SharedProfileDto candidate = profile(20, preferences(18, 99, 100));

        assertThat(strategy.calculateScore(viewer, candidate))
                .isCloseTo(0.5 * strategy.getWeight(), within(1e-9));
    }

    @Test
    void scoresNeutralWhenViewerPreferencesAreUnknown() {
        SharedProfileDto viewer = profile(30, null);
        SharedProfileDto candidate = profile(28, preferences(18, 99, 100));

        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(0.5);
    }

    @Test
    void proximityFallsBackToTheDefaultRadiusWhenTheViewerRadiusIsMissing() {
        // Given: preferences present but maxRange never set — the record declares it
        // @NotNull, yet @JsonIgnoreProperties deserialization does not enforce that
        LocationProximityStrategy proximity = new LocationProximityStrategy();
        SharedProfileDto viewer = profile(30, new SharedPreferencesDto(18, 99, "ANY", null), 0.0, 0.0);
        SharedProfileDto candidate = profile(28, preferences(18, 99, 100), 0.0, 0.0);

        // When / Then: the 100 km default applies instead of an unboxing NullPointerException
        assertThat(proximity.calculateScore(viewer, candidate)).isEqualTo(proximity.getWeight());
    }

    @Test
    void scoresNeutralWhenCandidateAgeIsMissing() {
        // Given: a candidate whose age was never set — SharedProfileDto.age is a nullable Integer
        SharedProfileDto viewer = profile(30, preferences(25, 35, 100));
        SharedProfileDto candidate = profile(null, preferences(18, 99, 100));

        // When / Then: neutral instead of the unboxing NullPointerException this used to throw
        assertThat(strategy.calculateScore(viewer, candidate)).isEqualTo(0.5);
    }
}
