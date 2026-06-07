package com.tinder.profiles.domain.profile;

import com.tinder.contracts.event.v1.ChangeType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Accumulates which profile fields changed during an update/patch and derives
 * the resulting {@link ChangeType}. A domain value object: pure logic, no
 * framework or persistence dependencies.
 *
 * <p>Replaces the {@code detectChangedFields} + {@code determineChangeType}
 * helpers that used to live in the application service. ({@code ChangeType}
 * comes from {@code tinder-contracts}, a pure shared library.)
 */
public final class ProfileChangeSet {

    private static final String FIELD_CITY = "city";
    private static final String FIELD_PREFERENCES = "preferences";
    private static final Set<String> CRITICAL_FIELDS = Set.of("age", "gender");

    private final Set<String> changedFields;
    private boolean preferencesChanged;

    private ProfileChangeSet() {
        this.changedFields = new HashSet<>();
    }

    public static ProfileChangeSet empty() {
        return new ProfileChangeSet();
    }

    /** Records that {@code field} changed. */
    public ProfileChangeSet add(String field) {
        changedFields.add(field);
        return this;
    }

    /**
     * Records that matching preferences changed. Also adds {@code "preferences"}
     * to the changed-field set so it surfaces in the emitted event payload.
     */
    public ProfileChangeSet markPreferencesChanged() {
        this.preferencesChanged = true;
        changedFields.add(FIELD_PREFERENCES);
        return this;
    }

    public boolean has(String field) {
        return changedFields.contains(field);
    }

    public boolean preferencesChanged() {
        return preferencesChanged;
    }

    /** True when nothing effectively changed. */
    public boolean isEmpty() {
        return changedFields.isEmpty() && !preferencesChanged;
    }

    /** Defensive, unmodifiable view of the changed-field names. */
    public Set<String> changedFields() {
        return Collections.unmodifiableSet(changedFields);
    }

    /**
     * Classifies the change by priority: city → {@link ChangeType#LOCATION_CHANGE};
     * else preferences → {@link ChangeType#PREFERENCES}; else age/gender →
     * {@link ChangeType#CRITICAL_FIELDS}; otherwise {@link ChangeType#NON_CRITICAL}.
     */
    public ChangeType classify() {
        if (changedFields.contains(FIELD_CITY)) {
            return ChangeType.LOCATION_CHANGE;
        }
        if (preferencesChanged) {
            return ChangeType.PREFERENCES;
        }
        for (String field : changedFields) {
            if (CRITICAL_FIELDS.contains(field)) {
                return ChangeType.CRITICAL_FIELDS;
            }
        }
        return ChangeType.NON_CRITICAL;
    }
}
