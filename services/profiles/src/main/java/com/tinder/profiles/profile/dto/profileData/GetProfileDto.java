package com.tinder.profiles.profile.dto.profileData;

import com.tinder.profiles.profile.Profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for {@link Profile}
 */
public record GetProfileDto(
        UUID profileId,
        String name,
        Integer age,
        String bio,
        String city,
        boolean isActive,
        LocalDateTime createdAt) {
}