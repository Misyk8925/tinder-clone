package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.Profile;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for the profile caching concern, consolidated behind a single
 * interface so a use case sees one cache dependency instead of the five cache
 * services it fans out to (entity cache, identity, shared-snapshot,
 * deck-snapshot, deck-page, hot-path token).
 *
 * <p>Mirrors the private cache helpers that currently live in
 * {@code ProfileApplicationService} ({@code putInCache}, {@code evictFromCache},
 * {@code refreshProfileCaches}, {@code evictReadCaches}).
 */
public interface ProfileCachePort {

    /** Looks up the cached profile by id, if present. */
    Optional<Profile> find(UUID profileId);

    /** Caches the profile entity under its id. */
    void put(Profile profile);

    /** Evicts the profile entity from the primary cache. */
    void evict(UUID profileId);

    /**
     * Refresh after a write: re-cache the entity, refresh the identity mapping,
     * and evict the read-model snapshots (shared, deck, deck-page, hot token) so
     * they rebuild from the new state.
     */
    void refreshOnWrite(String userId, Profile profile);

    /**
     * Evict everything tied to a profile on delete: identity mapping plus all
     * read-model snapshots.
     */
    void evictReadModels(String userId, UUID profileId);

    /** Bulk eviction for batch deletes. */
    void evictBatch(Collection<UUID> profileIds, Collection<String> userIds);
}
