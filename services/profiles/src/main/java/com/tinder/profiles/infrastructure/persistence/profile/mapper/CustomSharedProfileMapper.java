package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.contracts.dto.SharedLocationDto;
import com.tinder.contracts.dto.SharedPhotoDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.profiles.infrastructure.persistence.location.Location;
import com.tinder.profiles.infrastructure.persistence.photos.Photo;
import com.tinder.profiles.infrastructure.persistence.photos.SharedPhotoMapper;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        return toSharedProfileDtos(List.of(profile)).get(0);
    }

    @Override
    public List<SharedProfileDto> toSharedProfileDtos(List<ProfileJpaEntity> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        Map<UUID, Collection<Photo>> photosByProfileId = new LinkedHashMap<>(profiles.size());
        for (ProfileJpaEntity profile : profiles) {
            if (profile != null && profile.getPhotos() != null) {
                photosByProfileId.put(profile.getProfileId(), profile.getPhotos());
            }
        }
        Map<UUID, List<SharedPhotoDto>> photos = sharedPhotoMapper.toDtosByProfileId(photosByProfileId);

        List<SharedProfileDto> dtos = new ArrayList<>(profiles.size());
        for (ProfileJpaEntity profile : profiles) {
            dtos.add(profile == null
                    ? null
                    : toSharedProfileDto(profile, photos.getOrDefault(profile.getProfileId(), List.of())));
        }
        return dtos;
    }

    private SharedProfileDto toSharedProfileDto(ProfileJpaEntity profile, List<SharedPhotoDto> photos) {
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
                photos,
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
