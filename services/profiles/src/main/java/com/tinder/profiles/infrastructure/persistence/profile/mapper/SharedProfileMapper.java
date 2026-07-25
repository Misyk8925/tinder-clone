package com.tinder.profiles.infrastructure.persistence.profile.mapper;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.contracts.dto.SharedProfileDto;

/**
 * Maps the JPA entity to the cross-service {@link SharedProfileDto}. Implemented
 * by hand in {@link CustomSharedProfileMapper} (Point → latitude/longitude needs
 * custom logic, so MapStruct generation was dropped).
 */
public interface SharedProfileMapper {

    SharedProfileDto toSharedProfileDto(ProfileJpaEntity profile);
}
