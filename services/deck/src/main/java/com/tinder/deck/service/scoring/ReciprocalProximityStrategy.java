package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

/**
 * Mirror of {@link LocationProximityStrategy}: the distance between two profiles is
 * symmetric, but the search radius is not. A viewer willing to travel 100 km still
 * cannot match someone 80 km away whose own radius is 20 km.
 *
 * <p>Scores the same distance against the candidate's radius so the deck stops promoting
 * profiles that can never see the viewer.
 */
@Component
public class ReciprocalProximityStrategy implements ScoringStrategy {

    /** Same fallback radius {@link LocationProximityStrategy} uses when preferences are unknown. */
    private static final int DEFAULT_MAX_RANGE = 100;

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        if (viewer.location() == null || candidate.location() == null) {
            return 0.0;
        }

        if (viewer.location().latitude() == null || viewer.location().longitude() == null ||
            candidate.location().latitude() == null || candidate.location().longitude() == null) {
            return 0.0;
        }

        double distance = LocationProximityStrategy.calculateDistance(
                viewer.location().latitude(),
                viewer.location().longitude(),
                candidate.location().latitude(),
                candidate.location().longitude()
        );

        SharedPreferencesDto preferences = candidate.preferences();
        Integer candidateRange = preferences != null ? preferences.maxRange() : null;
        int maxRange = candidateRange != null ? candidateRange : DEFAULT_MAX_RANGE;

        if (distance <= maxRange) {
            return (1.0 - distance / maxRange) * getWeight();
        }

        return 0.0;
    }

    @Override
    public double getWeight() {
        return 0.8;
    }
}
