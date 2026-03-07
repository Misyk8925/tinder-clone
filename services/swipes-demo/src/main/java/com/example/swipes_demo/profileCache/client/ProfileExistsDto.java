package com.example.swipes_demo.profileCache.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Minimal projection of the profiles service SharedProfileDto.
 * Only extracts the profileId needed for existence checks.
 * Unknown fields (name, age, bio, etc.) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileExistsDto(UUID profileId) {

    @JsonCreator
    public ProfileExistsDto(@JsonProperty("profileId") UUID profileId) {
        this.profileId = profileId;
    }
}
