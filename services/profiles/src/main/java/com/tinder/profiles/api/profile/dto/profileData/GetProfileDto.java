package com.tinder.profiles.api.profile.dto.profileData;

import com.tinder.contracts.dto.Hobby;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for a profile
 */
public record GetProfileDto(
        UUID profileId,
        String userId,
        String name,
        Integer age,
        String gender,
        String bio,
        String city,
        boolean isActive,
        LocalDateTime createdAt,
        PreferencesDto preferences,
        List<PhotoDto> photos,
        List<Hobby> hobbies) {
}
