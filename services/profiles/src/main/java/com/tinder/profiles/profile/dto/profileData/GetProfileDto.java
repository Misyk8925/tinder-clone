package com.tinder.profiles.profile.dto.profileData;

import com.tinder.profiles.hobbies.Hobby;
import com.tinder.profiles.photos.Photo;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.dto.profileData.shared.SharedPreferencesDto;

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
        String gender,
        String bio,
        String city,
        boolean isActive,
        LocalDateTime createdAt,
        SharedPreferencesDto preferences,
        List<Photo> photos,
        List<Hobby> hobbies) {
}