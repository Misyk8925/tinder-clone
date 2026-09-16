package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

/**
 * Mirror of {@link AgeCompatibilityStrategy}: scores the viewer's age against the
 * candidate's preferred age range instead of the other way round.
 *
 * <p>Without it the deck ranks candidates the viewer would like while ignoring whether
 * those candidates could ever like the viewer back. A match needs both sides, so a
 * one-directional score fills the deck with unreachable profiles.
 */
@Component
public class ReciprocalAgeStrategy implements ScoringStrategy {

    /** Same neutral value {@link AgeCompatibilityStrategy} uses when preferences are unknown. */
    private static final double UNKNOWN_PREFERENCES_SCORE = 0.5;

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        SharedPreferencesDto preferences = candidate.preferences();
        if (preferences == null) return UNKNOWN_PREFERENCES_SCORE;

        Integer minAge = preferences.minAge();
        Integer maxAge = preferences.maxAge();

        if (minAge == null || maxAge == null) return UNKNOWN_PREFERENCES_SCORE;

        Integer viewerAge = viewer.age();
        if (viewerAge == null) return UNKNOWN_PREFERENCES_SCORE;

        return AgeCompatibilityStrategy.ageProximityFactor(viewerAge, minAge, maxAge) * getWeight();
    }

    @Override
    public double getWeight() {
        return 1.0;
    }
}
