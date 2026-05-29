package com.tinder.profiles.profile.dto.profileData.deck;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DeckPhotoDto(
        @JsonProperty("photoID") UUID photoId,
        String url,
        int position,
        boolean isPrimary
) {
}
