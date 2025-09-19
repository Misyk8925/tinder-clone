package com.tinder.profiles.profile;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface ProfileMapper {
    Profile toEntity(CreateProfileDtoV1 createProfileDtoV1);

    CreateProfileDtoV1 toCreateProfileDtoV1(Profile profile);
}