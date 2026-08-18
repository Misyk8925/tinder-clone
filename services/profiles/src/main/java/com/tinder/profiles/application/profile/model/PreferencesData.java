package com.tinder.profiles.application.profile.model;

/**
 * Matching criteria as they cross the application boundary, in both directions:
 * inbound on the commands, outbound on the query views.
 *
 * <p>Deliberately unvalidated data — the invariants live in the domain value
 * object {@code MatchingPreferences}, which the application layer builds from
 * this record (see {@code ProfileEditService#toEdit}).
 */
public record PreferencesData(Integer minAge, Integer maxAge, String gender, Integer maxRange) {
}
