package com.tinder.profiles.profile.mapper;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.profile.dto.profileData.shared.SharedProfileDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SharedProfileMapper {
    Profile toEntity(GetProfileDto getProfileDto);

    SharedProfileDto toSharedProfileDto(Profile profile);
}