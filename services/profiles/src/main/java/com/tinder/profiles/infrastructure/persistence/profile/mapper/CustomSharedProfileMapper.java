package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.photos.SharedPhotoMapper;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper for the SharedProfile DTO: Point → latitude/longitude
 * needs custom logic that MapStruct cannot generate.
 */
@Component
@RequiredArgsConstructor
public class CustomSharedProfileMapper implements SharedProfileMapper {

    private final SharedPhotoMapper sharedPhotoMapper;

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
                sharedPhotoMapper.toDtos(profile.getProfileId(), profile.getPhotos()),
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
}
