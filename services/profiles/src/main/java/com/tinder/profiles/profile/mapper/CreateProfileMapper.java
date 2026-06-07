package com.tinder.profiles.profile.mapper;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.api.profile.dto.profileData.CreateProfileDtoV1;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface CreateProfileMapper {
    ProfileJpaEntity toEntity(CreateProfileDtoV1 createProfileDtoV1);

    CreateProfileDtoV1 toCreateProfileDtoV1(ProfileJpaEntity profile);
}