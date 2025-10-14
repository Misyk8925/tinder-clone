package com.tinder.profiles.profile.mapper;

import com.tinder.profiles.preferences.PreferencesDto;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.dto.profileData.ProfileDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProfileMapper {
    
    // Конвертация Profile в ProfileDto
    @Mapping(source = "preferences", target = "preferences")
    ProfileDto toDto(Profile profile);
    
    // Конвертация ProfileDto в Profile
    Profile toEntity(ProfileDto profileDto);
    
    // Метод для конвертации Preferences в PreferencesDto
    default PreferencesDto mapPreferences(com.tinder.profiles.preferences.Preferences preferences) {
        if (preferences == null) {
            return null;
        }
        return new PreferencesDto(
                preferences.getId(),
                preferences.getMinAge(),
                preferences.getMaxAge(),
                preferences.getGender(),
                preferences.getMaxRange()
        );
    }
}

