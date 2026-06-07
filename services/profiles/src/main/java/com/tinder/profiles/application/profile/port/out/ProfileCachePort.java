package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.Profile;

import java.util.Collection;
import java.util.UUID;

/**
 * Outbound port for the profile caching concern, consolidated behind a single
 * interface so a use case sees one cache dependency instead of the cache services
 * it fans out to (entity cache, identity, shared-snapshot, deck-snapshot,
 * deck-page, hot-path token).
 *
 * <p>The write path <em>evicts</em> rather than re-populates the entity cache:
 * the domain {@link Profile} aggregate excludes photos (which the read DTO
 * carries) and is not a serializable cache shape, so the read path
 * ({@code getOne}) repopulates the entity cache from the full JPA entity on the
 * next read. This keeps a single, consistent value type in the entity cache.
 */
public interface ProfileCachePort {

    /** Evicts the profile entity from the primary cache. */
    void evict(UUID profileId);

    /**
     * Refresh after a write: evict the stale entity-cache entry, refresh the
     * identity mapping, and evict the read-model snapshots (shared, deck,
     * deck-page, hot token) so they rebuild from the new state.
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
