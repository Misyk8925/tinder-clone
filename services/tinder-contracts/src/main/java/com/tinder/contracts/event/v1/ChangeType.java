package com.tinder.contracts.event.v1;

/**
 * Classifies what changed in a {@link ProfileUpdatedEvent}.
 * Consumers use this to decide how urgently to react (e.g. immediate deck rebuild vs. lazy stale-mark).
 */
public enum ChangeType {

    /**
     * Changes to discovery preferences (age range, gender preference, distance).
     * Requires immediate deck rebuild for the profile owner.
     */
    PREFERENCES,

    /**
     * Changes to fields that affect how others see this profile (age, gender, photos).
     * Profile should be marked stale in other users' decks.
     */
    CRITICAL_FIELDS,

    /**
     * City or coordinates updated.
     * Requires deck invalidation for the owner and stale-marking for viewers.
     */
    LOCATION_CHANGE,

    /**
     * Low-priority changes (bio, display name, etc.).
     * Can be processed asynchronously with lower urgency.
     */
    NON_CRITICAL
}
