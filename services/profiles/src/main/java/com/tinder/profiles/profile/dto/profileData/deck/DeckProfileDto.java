package com.tinder.profiles.profile.dto.profileData.deck;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinder.profiles.hobbies.Hobby;

import java.util.List;
import java.util.UUID;

public record DeckProfileDto(
        @JsonProperty("profileId") UUID id,
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        List<DeckPhotoDto> photos,
        List<Hobby> hobbies
) {
}
