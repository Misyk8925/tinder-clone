package com.tinder.deck.kafka.dto;

/**
 * Enum representing the type of profile change
 */
public enum ChangeType {
    /**
     * Changes to user preferences (age range, gender preference, distance, etc.)
     * Requires immediate deck rebuild for the profile owner
     */
    PREFERENCES,

    /**
     * Changes to critical profile fields (age, gender, photos)
     * Profile should be marked as stale in other users' decks
     */
    CRITICAL_FIELDS,

    /**
     * Location updates that affect both the owner's deck and viewers' decks.
     */
    LOCATION_CHANGE,

    /**
     * Non-critical changes (bio, name, etc.)
     * Can be handled with lower priority
     */
    NON_CRITICAL
}
