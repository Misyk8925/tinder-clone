package com.tinder.deck.dto;



import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record SharedProfileDto(@JsonProperty("profileId") UUID id,
                               String name,
                               Integer age,
                               String bio,
                               String city,
                               boolean isActive,
                               SharedLocationDto location,
                               SharedPreferencesDto preferences,
                               boolean isDeleted,
                               List<SharedPhotoDto> photos) {
}