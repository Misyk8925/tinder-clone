package com.tinder.profiles.application.profile;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Page<ProfileJpaEntity> getAll(Pageable pageable) {
        return profileRepository.findAll(pageable);
    }

    public GetProfileDto getOne(UUID id) {
        try {
            Cache.ValueWrapper profileCache = resilientCacheManager.get(PROFILE_CACHE_NAME, id);
            if (profileCache != null && profileCache.get() instanceof ProfileJpaEntity profile) {
                return profile.isDeleted() ? null : getMapper.toGetProfileDto(profile);
            }

            ProfileJpaEntity profile = profileRepository.findById(id)
                    .orElseThrow(() -> new ProfileNotFoundException(id.toString(), "id"));
            if (profile.isDeleted()) {
                return null;
            }
            resilientCacheManager.put(PROFILE_CACHE_NAME, id, profile);
            return getMapper.toGetProfileDto(profile);

        } catch (ProfileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving profile {}: {}. Loading from database.", id, e.getMessage());
            ProfileJpaEntity profile = profileRepository.findById(id)
                    .orElseThrow(() -> new ProfileNotFoundException(id.toString(), "id"));
            return profile.isDeleted() ? null : getMapper.toGetProfileDto(profile);
        }
    }

    public ProfileJpaEntity getByUsername(String username) {
        return profileRepository.findByName(username);
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
