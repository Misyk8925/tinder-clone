package com.tinder.profiles.infrastructure.profile.cache;

import com.tinder.profiles.application.profile.port.out.ProfileCachePort;
import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.cache.DeckPageCacheService;
import com.tinder.profiles.infrastructure.cache.DeckProfileSnapshotCache;
import com.tinder.profiles.infrastructure.cache.ProfileIdentityCacheService;
import com.tinder.profiles.infrastructure.cache.SharedProfileSnapshotCache;
import com.tinder.profiles.infrastructure.cache.ResilientCacheManager;
import com.tinder.profiles.infrastructure.cache.DeckHotPathTokenCache;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache adapter implementing {@link ProfileCachePort} by fanning out to the five
 * existing cache services. Consolidates the cache-name constant and the
 * eviction fan-out that currently live as private helpers in
 * {@code ProfileApplicationService}.
 *
 * <p>Note: this adapter caches the <em>domain</em> {@link Profile} (the port is
 * domain-typed), whereas the legacy entity cache stored the JPA entity. The
 * cache name is shared, so the two must not be mixed at runtime — relevant only
 * once a use case is wired onto this port.
 */
@Component
@RequiredArgsConstructor
public class RedisProfileCacheAdapter implements ProfileCachePort {

    static final String PROFILE_CACHE_NAME = "PROFILE_ENTITY_CACHE";

    private final ResilientCacheManager resilientCacheManager;
    private final ProfileIdentityCacheService profileIdentityCacheService;
    private final SharedProfileSnapshotCache sharedProfileSnapshotCache;
    private final DeckProfileSnapshotCache deckProfileSnapshotCache;
    private final DeckPageCacheService deckPageCacheService;
    private final DeckHotPathTokenCache deckHotPathTokenCache;

    @Override
    public Optional<Profile> find(UUID profileId) {
        Cache.ValueWrapper wrapper = resilientCacheManager.get(PROFILE_CACHE_NAME, profileId);
        if (wrapper == null) {
            return Optional.empty();
        }
        Object cached = wrapper.get();
        return cached instanceof Profile profile ? Optional.of(profile) : Optional.empty();
    }

    @Override
    public void put(Profile profile) {
        resilientCacheManager.put(PROFILE_CACHE_NAME, profile.getId(), profile);
    }

    @Override
    public void evict(UUID profileId) {
        resilientCacheManager.evict(PROFILE_CACHE_NAME, profileId);
    }

    @Override
    public void refreshOnWrite(String userId, Profile profile) {
        UUID profileId = profile.getId();
        put(profile);
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
        profileIds.forEach(deckProfileSnapshotCache::evict);
        profileIds.forEach(deckHotPathTokenCache::evictProfile);
        deckPageCacheService.evictViewers(profileIds);
        userIds.forEach(profileIdentityCacheService::evict);
    }

    private void evictReadModelSnapshots(UUID profileId) {
        sharedProfileSnapshotCache.evict(profileId);
        deckProfileSnapshotCache.evict(profileId);
        deckPageCacheService.evictViewer(profileId);
        deckHotPathTokenCache.evictProfile(profileId);
    }
}
