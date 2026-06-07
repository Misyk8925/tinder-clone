package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.api.profile.dto.profileData.GetProfileDto;
import com.tinder.contracts.dto.SharedProfileDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SharedProfileMapper {
    ProfileJpaEntity toEntity(GetProfileDto getProfileDto);

    SharedProfileDto toSharedProfileDto(ProfileJpaEntity profile);
}