package com.tinder.profiles.infrastructure.cache;

import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Cache adapter implementing {@link ProfileCachePort} by fanning out to the
 * existing cache services. Consolidates the cache-name constant and the eviction
 * fan-out that used to live as private helpers in {@code JpaProfileQueryAdapter}.
 *
 * <p>The write path evicts the entity cache rather than storing the domain
 * aggregate (which lacks photos and is not a serializable cache shape); the read
 * path repopulates it with the full JPA entity on the next {@code getOne}.
 */
@Component
@RequiredArgsConstructor
public class RedisProfileCacheAdapter implements ProfileCachePort {

    static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";

    private final ResilientCacheManager resilientCacheManager;
    private final ProfileIdentityCacheService profileIdentityCacheService;
    private final SharedProfileSnapshotCache sharedProfileSnapshotCache;

    @Override
    public void evict(UUID profileId) {
        resilientCacheManager.evict(PROFILE_CACHE_NAME, profileId);
    }

    @Override
    public void refreshOnWrite(String userId, Profile profile) {
        UUID profileId = profile.getId();
        evict(profileId);
        profileIdentityCacheService.put(userId, profileId);
        evictReadModelSnapshots(profileId);
    }

    @Override
    public void evictReadModels(String userId, UUID profileId) {
        profileIdentityCacheService.evict(userId);
        evictReadModelSnapshots(profileId);
    }

    @Override
    public void evictBatch(Collection<UUID> profileIds, Collection<String> userIds) {
        profileIds.forEach(this::evict);
        profileIds.forEach(sharedProfileSnapshotCache::evict);
        userIds.forEach(profileIdentityCacheService::evict);
    }

    private void evictReadModelSnapshots(UUID profileId) {
        sharedProfileSnapshotCache.evict(profileId);
    }
}
