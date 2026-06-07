package com.tinder.profiles.domain.profile;

import java.util.Objects;

/**
 * The matching criteria a user looks for: age band, target gender and maximum
 * search range (km). A self-validating domain value object — an instance cannot
 * exist in an invalid state.
 */
public record MatchingPreferences(Integer minAge, Integer maxAge, String gender, Integer maxRange) {

    public MatchingPreferences {
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new DomainValidationException("Minimum age cannot be greater than maximum age");
        }
    }

    /**
     * True when {@code other} represents the same matching criteria. Used to
     * decide whether a preferences change should emit a {@code PREFERENCES}
     * event (replaces the old {@code preferencesEqual} helper).
     */
    public boolean sameCriteriaAs(MatchingPreferences other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(minAge, other.minAge)
                && Objects.equals(maxAge, other.maxAge)
                && Objects.equals(gender, other.gender)
                && Objects.equals(maxRange, other.maxRange);
    }
}
