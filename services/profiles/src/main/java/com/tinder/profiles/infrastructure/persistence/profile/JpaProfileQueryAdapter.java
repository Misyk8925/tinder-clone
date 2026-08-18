package com.tinder.profiles.infrastructure.persistence.profile;

import com.tinder.profiles.application.profile.port.in.ProfileQuery;
import com.tinder.profiles.application.profile.model.PreferencesData;
import com.tinder.profiles.application.profile.query.ProfileView;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.infrastructure.cache.ProfileIdentityCacheService;
import com.tinder.profiles.infrastructure.cache.ResilientCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read-side application service for profiles (queries only). The write path —
 * create / update / patch / delete / premium — lives in the
 * {@code application.profile.usecase} services, which operate on the domain
 * aggregate through the outbound ports.
 *
 * <p>Reads deliberately stay on the JPA read path and map to an
 * application-owned query model. Routing them through the write aggregate would
 * add mapping work without enforcing an invariant (CQRS asymmetry).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaProfileQueryAdapter implements ProfileQuery {

    private final ProfileRepository profileRepository;
    private final ResilientCacheManager resilientCacheManager;
    private final ProfileIdentityCacheService profileIdentityCacheService;

    private static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";

    @Override
    public ProfileView getOne(UUID id) {
        // ResilientCacheManager fails open (returns null / no-ops on Redis errors),
        // so no extra try/catch fallback is needed: cache-lookup → DB-load → cache.
        // Soft-deleted profiles are indistinguishable from missing ones to callers.
        Cache.ValueWrapper cached = resilientCacheManager.get(PROFILE_CACHE_NAME, id);
        if (cached != null && cached.get() instanceof ProfileJpaEntity p) {
            if (p.isDeleted()) {
                throw new ProfileNotFoundException(id.toString(), "id");
            }
            return toView(p);
        }

        ProfileJpaEntity profile = profileRepository.findById(id)
                .orElseThrow(() -> new ProfileNotFoundException(id.toString(), "id"));
        if (profile.isDeleted()) {
            throw new ProfileNotFoundException(id.toString(), "id");
        }
        // Map before caching: mapping touches the lazy photo/hobby collections, so the
        // entity is fully initialized when it is serialized into the Redis cache
        // (an uninitialized proxy poisons the cached JSON for subsequent reads).
        ProfileView view = toView(profile);
        resilientCacheManager.put(PROFILE_CACHE_NAME, id, profile);
        return view;
    }

    @Override
    public UUID getActiveProfileIdByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return profileIdentityCacheService.getProfileId(userId, profileRepository::findActiveProfileIdByUserId);
    }

    @Override
    public ProfileView getMyProfile(String userId) {
        return toView(profileRepository.findByUserId(userId));
    }

    private ProfileView toView(ProfileJpaEntity profile) {
        if (profile == null || profile.isDeleted()) {
            return null;
        }
        PreferencesData preferences = profile.getPreferences() == null
                ? null
                : new PreferencesData(
                        profile.getPreferences().getMinAge(),
                        profile.getPreferences().getMaxAge(),
                        profile.getPreferences().getGender(),
                        profile.getPreferences().getMaxRange());
        java.util.List<ProfileView.PhotoView> photos = profile.getPhotos() == null
                ? java.util.List.of()
                : profile.getPhotos().stream()
                        .map(photo -> new ProfileView.PhotoView(
                                photo.getPhotoID(),
                                photo.getS3Key(),
                                photo.isPrimary(),
                                photo.getPosition(),
                                photo.getUrl(),
                                photo.getContentType(),
                                photo.getSize(),
                                photo.getCreatedAt()))
                        .toList();
        java.util.List<String> hobbies = profile.getHobbies() == null
                ? java.util.List.of()
                : profile.getHobbies().stream().map(Enum::name).toList();
        return new ProfileView(
                profile.getProfileId(),
                profile.getUserId(),
                profile.getName(),
                profile.getAge(),
                profile.getGender(),
                profile.getBio(),
                profile.getCity(),
                profile.isActive(),
                profile.getCreatedAt(),
                preferences,
                photos,
                hobbies);
    }
}
