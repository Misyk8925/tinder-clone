package com.tinder.deckread.dto;

import com.tinder.contracts.dto.Hobby;

import java.util.List;
import java.util.UUID;

/** Exact deprecated v1 wire shape; adapted from the richer local card projection. */
public record DeckCardV1Dto(
        UUID profileId,
        String name,
        Integer age,
        String city,
        String bio,
        List<Photo> photos,
        List<Hobby> hobbies
) {
    public DeckCardV1Dto {
        photos = List.copyOf(photos);
        hobbies = List.copyOf(hobbies);
    }

    public static DeckCardV1Dto from(DeckCardDto card) {
        return new DeckCardV1Dto(
                card.profileId(), card.name(), card.age(), card.city(), card.bio(),
                card.photos().stream()
                        .map(photo -> new Photo(photo.url(), photo.order(), photo.order() == 0))
                        .toList(),
                card.hobbies());
    }

    public record Photo(String url, int position, boolean isPrimary) {
    }
}
