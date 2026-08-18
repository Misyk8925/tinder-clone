package com.tinder.profiles.infrastructure.cache;

import com.tinder.profiles.domain.profile.Profile;
import com.tinder.profiles.infrastructure.cache.ProfileIdentityCacheService;
import com.tinder.profiles.infrastructure.cache.SharedProfileSnapshotCache;
import com.tinder.profiles.infrastructure.cache.ResilientCacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisProfileCacheAdapter")
class RedisProfileCacheAdapterTest {

    @Mock private ResilientCacheManager resilientCacheManager;
    @Mock private ProfileIdentityCacheService profileIdentityCacheService;
    @Mock private SharedProfileSnapshotCache sharedProfileSnapshotCache;

    private RedisProfileCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisProfileCacheAdapter(resilientCacheManager, profileIdentityCacheService,
                sharedProfileSnapshotCache);
    }

    private static Profile profile(UUID id) {
        return Profile.builder().id(id).userId("user-1").name("Alice").city("Vienna").build();
    }

    @Test
    @DisplayName("refreshOnWrite evicts the stale entity entry, refreshes identity and evicts the read model")
    void refreshOnWrite() {
        UUID id = UUID.randomUUID();
        Profile profile = profile(id);

        adapter.refreshOnWrite("user-1", profile);

        verify(resilientCacheManager).evict(RedisProfileCacheAdapter.PROFILE_CACHE_NAME, id);
        verify(profileIdentityCacheService).put("user-1", id);
        verify(sharedProfileSnapshotCache).evict(id);
    }

    @Test
    @DisplayName("evictReadModels clears the identity mapping and the shared read-model snapshot")
    void evictReadModels() {
        UUID id = UUID.randomUUID();

        adapter.evictReadModels("user-1", id);

        verify(profileIdentityCacheService).evict("user-1");
        verify(sharedProfileSnapshotCache).evict(id);
    }

    @Test
    @DisplayName("evictBatch fans out across the entity and shared caches for every id")
    void evictBatch() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<UUID> ids = List.of(a, b);

        adapter.evictBatch(ids, List.of("user-1"));

        verify(resilientCacheManager).evict(RedisProfileCacheAdapter.PROFILE_CACHE_NAME, a);
        verify(resilientCacheManager).evict(RedisProfileCacheAdapter.PROFILE_CACHE_NAME, b);
        verify(sharedProfileSnapshotCache).evict(a);
        verify(sharedProfileSnapshotCache).evict(b);
        verify(profileIdentityCacheService).evict("user-1");
    }
}
