package com.tinder.profiles.domain.profile;

import java.util.Objects;

/**
 * Pure domain logic that spans the {@link Profile} aggregate and an incoming
 * {@link ProfileEdit} but does not belong to either alone: cross-field
 * validation and change detection.
 *
 * <p>Deliberately framework-free — no Spring, JPA, sanitization or API types,
 * and no Lombok. It is a plain class (instantiated as a bean by the application
 * layer), so it is trivially unit-testable without a Spring context.
 * Preferences validation lives in {@link MatchingPreferences} (self-validating
 * value object); event classification lives in {@link ProfileChangeSet}.
 */
public class ProfileDomainService {

    /**
     * A profile must be locatable: the edit must provide either a city or
     * resolved coordinates.
     */
    public void requireLocationProvided(ProfileEdit edit) {
        if (!edit.hasCity() && !edit.hasPosition()) {
            throw new DomainValidationException("Either city or GPS coordinates must be provided");
        }
    }

    /**
     * Computes which fields differ between the persisted profile and the
     * incoming edit. Only fields present on the edit (non-null) are considered,
     * so the same method serves both full updates and partial patches.
     *
     * <p>Coordinate-driven city changes (GPS movement past the jitter threshold)
     * are not detected here — the application layer owns the movement check and
     * the location resolution, then records the resulting {@code "city"} change
     * on the returned set.
     */
    public ProfileChangeSet detectChanges(Profile existing, ProfileEdit incoming) {
        ProfileChangeSet changes = ProfileChangeSet.empty();

        if (incoming.name() != null && !Objects.equals(existing.getName(), incoming.name())) {
            changes.add("name");
        }
        if (incoming.age() != null && !Objects.equals(existing.getAge(), incoming.age())) {
            changes.add("age");
        }
        if (incoming.gender() != null && !Objects.equals(existing.getGender(), incoming.gender())) {
            changes.add("gender");
        }
        if (incoming.bio() != null && !Objects.equals(existing.getBio(), incoming.bio())) {
            changes.add("bio");
        }
        if (incoming.city() != null && !Objects.equals(existing.getCity(), incoming.city())) {
            changes.add("city");
        }
        if (incoming.preferences() != null) {
            MatchingPreferences current = existing.getPreferences();
            if (current == null || !current.sameCriteriaAs(incoming.preferences())) {
                changes.markPreferencesChanged();
            }
        }
        if (incoming.hobbies() != null) {
            changes.add("hobbies");
        }

        return changes;
    }
}
