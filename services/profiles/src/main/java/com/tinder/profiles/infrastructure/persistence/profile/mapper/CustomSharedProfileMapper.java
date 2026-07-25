package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.photos.Photo;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Hand-written mapper for the SharedProfile DTO: Point → latitude/longitude
 * needs custom logic that MapStruct cannot generate.
 */
@Component
@RequiredArgsConstructor
public class CustomSharedProfileMapper implements SharedProfileMapper {

    @Override
    public SharedProfileDto toSharedProfileDto(ProfileJpaEntity profile) {
        if (profile == null) {
            return null;
        }

        return new SharedProfileDto(
                profile.getProfileId(),
                profile.getName(),
                profile.getAge(),
                profile.getBio(),
                profile.getCity(),
                profile.isActive(),
                locationToSharedLocationDto(profile.getLocation()),
                preferencesToSharedPreferencesDto(profile.getPreferences()),
                profile.isDeleted(),
                photosToSharedPhotoDtos(profile.getProfileId(), profile.getPhotos()),
                profile.getHobbies()
        );
    }

    private SharedLocationDto locationToSharedLocationDto(Location location) {
        if (location == null) {
            return null;
        }

        return new SharedLocationDto(
                location.getId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCity(),
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }

    private SharedPreferencesDto preferencesToSharedPreferencesDto(Preferences preferences) {
        if (preferences == null) {
            return null;
        }

        return new SharedPreferencesDto(
                preferences.getMinAge(),
                preferences.getMaxAge(),
                preferences.getGender(),
                preferences.getMaxRange()
        );
    }

    private List<SharedPhotoDto> photosToSharedPhotoDtos(java.util.UUID profileId, List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyList();
        }
        return photos.stream()
                .map(photo -> new SharedPhotoDto(
                        photo.getPhotoID(),
                        profileId,
                        photo.getS3Key(),
                        photo.isPrimary(),
                        photo.getPosition(),
                        photo.getUrl(),
                        photo.getContentType(),
                        photo.getSize(),
                        photo.getCreatedAt()
                ))
                .toList();
    }
}
