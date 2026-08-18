package com.tinder.deckread.dto;

import com.tinder.contracts.dto.Hobby;

import java.util.List;
import java.util.UUID;

/**
 * Deck Read-owned client projection. It is materialized from Kafka and never
 * hydrated synchronously from Profiles.
 */
public record DeckCardDto(
        UUID profileId,
        String name,
        Integer age,
        String city,
        String bio,
        boolean isActive,
        Preferences preferences,
        List<Photo> photos,
        List<Hobby> hobbies
) {
    public DeckCardDto {
        photos = photos == null ? List.of() : List.copyOf(photos);
        hobbies = hobbies == null ? List.of() : List.copyOf(hobbies);
    }

    public record Preferences(Integer minAge, Integer maxAge, String gender, Integer maxDistanceKm) {}

    public record Photo(UUID photoId, String url, int order) {}
}
