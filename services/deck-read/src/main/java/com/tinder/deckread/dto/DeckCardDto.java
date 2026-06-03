package com.tinder.deckread.dto;

import com.tinder.contracts.dto.Hobby;

import java.util.List;
import java.util.UUID;

/**
 * Lean swipe-card projection returned by {@code GET /api/v1/deck}.
 *
 * <p>Contains only the fields the discover/swipe UI actually renders (verified against the Angular
 * client's {@code swipe-card} and {@code discover} components): identity, name, age, city, bio,
 * ordered photo URLs, and hobbies. Internal/unused fields carried by the full profile
 * (gender, isActive, isDeleted, preferences, location, and per-photo s3Key/contentType/size/
 * createdAt/ids) are dropped — cutting response serialization, wire bytes to clients, and the
 * cache footprint.
 */
public record DeckCardDto(
        UUID profileId,
        String name,
        Integer age,
        String city,
        String bio,
        List<Photo> photos,
        List<Hobby> hobbies
) {
    /** Minimal photo: just what the carousel needs (URL + display order). */
    public record Photo(String url, int position, boolean isPrimary) {}
}
