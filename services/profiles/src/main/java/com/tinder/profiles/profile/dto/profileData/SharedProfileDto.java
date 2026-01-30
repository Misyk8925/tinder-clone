package com.tinder.profiles.profile.dto.profileData;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record SharedProfileDto(@JsonProperty("profileId") UUID id,
                               String name,
                               Integer age,
                               String bio,
                               String city,
                               boolean isActive,
                               SharedLocationDto location,
                               SharedPreferencesDto preferences,
                               boolean isDeleted) {
}