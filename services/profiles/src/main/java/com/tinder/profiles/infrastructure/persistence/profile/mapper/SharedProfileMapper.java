package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.contracts.dto.SharedProfileDto;

import java.util.List;

/**
 * Maps the JPA entity to the cross-service {@link SharedProfileDto}. Implemented
 * by hand in {@link CustomSharedProfileMapper} (Point → latitude/longitude needs
 * custom logic, so MapStruct generation was dropped).
 */
public interface SharedProfileMapper {

    SharedProfileDto toSharedProfileDto(ProfileJpaEntity profile);

    /** Maps many profiles, signing all their photo URLs in one call. Keeps input order. */
    List<SharedProfileDto> toSharedProfileDtos(List<ProfileJpaEntity> profiles);
}
