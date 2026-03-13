package com.tinder.profiles.profile.dto.profileData;

import com.tinder.profiles.photos.Photo;
import com.tinder.profiles.profile.Profile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Profile}
 */
public record GetProfileDto(
        UUID profileId,
        String userId,
        String name,
        Integer age,
        String bio,
        String city,
        boolean isActive,
        LocalDateTime createdAt,
        List<Photo> photos) {
}