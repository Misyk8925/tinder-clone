package com.tinder.profiles.profile.mapper;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.location.Location;
import com.tinder.profiles.photos.Photo;
import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Custom mapper implementation for SharedProfile DTO.
 * This overrides MapStruct auto-generated mapper to handle complex mappings like Point to latitude/longitude.
 */
@Primary
@Component
@RequiredArgsConstructor
public class CustomSharedProfileMapper implements SharedProfileMapper {

    @Override
    public Profile toEntity(GetProfileDto getProfileDto) {
        // Not implemented - not needed for current use case
        throw new UnsupportedOperationException("Conversion from GetProfileDto to Profile is not supported");
    }

    @Override
    public SharedProfileDto toSharedProfileDto(Profile profile) {
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
