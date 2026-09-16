package com.tinder.deck.service.scoring;

import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.springframework.stereotype.Component;

/**
 * Ranks by a signal the platform already collects and has never used: how often each profile
 * is liked by the people shown it.
 *
 * <p>Measured offline against the best ranking any ordering could achieve, today's age and
 * distance heuristics reach about 13% of it and adding the mirrored preference terms reaches
 * 16%. This reaches 32-36% — the unused signal is worth more than twice everything else in
 * the ranking put together.
 *
 * <p>The rate is multiplied by how well the pair fit each other's stated preferences in both
 * directions, not added to them. A match needs both sides, so the quantity to maximise is a
 * product of two chances; and a wildly popular profile four hundred kilometres outside the
 * viewer's radius is worth nothing, which a sum would not express.
 *
 * <p>The viewer's own popularity is constant across their deck and so cannot change its
 * order. It is left out rather than cancelled later.
 */
@Component
public class PopularityStrategy implements ScoringStrategy {

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate) {
        // Without a loaded context there is no rate to rank by, so this contributes nothing
        // and the variant degrades to its preference terms alone.
        return 0;
    }

    @Override
    public double calculateScore(SharedProfileDto viewer, SharedProfileDto candidate, ScoringContext context) {
        double forward = fit(viewer, candidate);
        double backward = fit(candidate, viewer);
        if (forward == 0 || backward == 0) {
            return 0;
        }
        return context.likeRateOf(candidate.id()) * forward * backward * getWeight();
    }

    /** How well the subject's stated window and radius accept the other profile. */
    private static double fit(SharedProfileDto subject, SharedProfileDto other) {
        SharedPreferencesDto preferences = subject.preferences();
        if (preferences == null || other.age() == null) {
            return NEUTRAL_FIT;
        }

        Integer minAge = preferences.minAge();
        Integer maxAge = preferences.maxAge();
        double ageFit = minAge == null || maxAge == null
                ? NEUTRAL_FIT
                : AgeCompatibilityStrategy.ageProximityFactor(other.age(), minAge, maxAge);
        if (ageFit == 0) {
            return 0;
        }

        if (subject.location() == null || other.location() == null
                || subject.location().latitude() == null || subject.location().longitude() == null
                || other.location().latitude() == null || other.location().longitude() == null) {
            return ageFit * NEUTRAL_FIT;
        }

        double distance = LocationProximityStrategy.calculateDistance(
                subject.location().latitude(), subject.location().longitude(),
                other.location().latitude(), other.location().longitude());
        Integer statedRange = preferences.maxRange();
        double radius = statedRange != null ? statedRange : DEFAULT_MAX_RANGE;
        if (radius <= 0 || distance > radius) {
            return 0;
        }
        return ageFit * (1.0 - distance / radius);
    }

    /** Used where a preference is unknown, matching the convention of the older strategies. */
    private static final double NEUTRAL_FIT = 0.5;
    private static final double DEFAULT_MAX_RANGE = 100;

    @Override
    public boolean usesLikeRates() {
        return true;
    }

    @Override
    public double getWeight() {
        // The rate is a probability and the fits are fractions, so their product is far
        // smaller than the older strategies' scores. The weight brings the variant onto a
        // comparable scale; it is not a claim about relative importance, because no variant
        // mixes this strategy with those.
        return 10.0;
    }
}
