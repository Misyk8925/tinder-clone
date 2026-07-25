package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.api.profile.dto.profileData.GetProfileDto;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.infrastructure.cache.ProfileIdentityCacheService;
import com.tinder.profiles.infrastructure.persistence.profile.mapper.GetProfileMapper;
import com.tinder.profiles.infrastructure.cache.ResilientCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Read-side application service for profiles (queries only). The write path —
 * create / update / patch / delete / premium — lives in the
 * {@code application.profile.usecase} services, which operate on the domain
 * aggregate through the outbound ports.
 *
 * <p>Reads deliberately stay on the JPA read path (entity + {@link GetProfileMapper}
 * + read-optimised projections); routing them through the aggregate would cost a
 * double mapping for no invariant gain (CQRS asymmetry). A follow-up (Stage 4)
 * extracts these into dedicated query services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileQueryService {

    private final ProfileRepository profileRepository;
    private final GetProfileMapper getMapper;
    private final ResilientCacheManager resilientCacheManager;
    private final ProfileIdentityCacheService profileIdentityCacheService;

    private static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";

    public GetProfileDto getOne(UUID id) {
        // ResilientCacheManager fails open (returns null / no-ops on Redis errors),
        // so no extra try/catch fallback is needed: cache-lookup → DB-load → cache.
        // Soft-deleted profiles are indistinguishable from missing ones to callers.
        Cache.ValueWrapper cached = resilientCacheManager.get(PROFILE_CACHE_NAME, id);
        if (cached != null && cached.get() instanceof ProfileJpaEntity p) {
            if (p.isDeleted()) {
                throw new ProfileNotFoundException(id.toString(), "id");
            }
            return getMapper.toGetProfileDto(p);
        }

        ProfileJpaEntity profile = profileRepository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException(id.toString(), "id"));
        if (profile.isDeleted()) {
            throw new ProfileNotFoundException(id.toString(), "id");
        }
        // Map before caching: mapping touches the lazy photo/hobby collections, so the
        // entity is fully initialized when it is serialized into the Redis cache
        // (an uninitialized proxy poisons the cached JSON for subsequent reads).
        GetProfileDto dto = getMapper.toGetProfileDto(profile);
        resilientCacheManager.put(PROFILE_CACHE_NAME, id, profile);
        return dto;
    }

    public ProfileJpaEntity getByUserId(String userId) {
        return profileRepository.findByUserId(userId);
    }

    public UUID getActiveProfileIdByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return profileIdentityCacheService.getProfileId(userId, profileRepository::findActiveProfileIdByUserId);
    }

    public GetProfileDto getMyProfile(String userID) {
        return getMapper.toGetProfileDto(getByUserId(userID));
    }
}
