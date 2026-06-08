package com.tinder.profiles.api.profile.dto.profileData;

import com.tinder.contracts.dto.Hobby;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.profiles.infrastructure.persistence.photos.Photo;

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
        SharedPreferencesDto preferences,
        List<Photo> photos,
        List<Hobby> hobbies) {
}