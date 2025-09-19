package com.tinder.profiles.profile.mapper;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.dto.profileData.CreateProfileDtoV1;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface CreateProfileMapper {
    Profile toEntity(CreateProfileDtoV1 createProfileDtoV1);

    CreateProfileDtoV1 toCreateProfileDtoV1(Profile profile);
}