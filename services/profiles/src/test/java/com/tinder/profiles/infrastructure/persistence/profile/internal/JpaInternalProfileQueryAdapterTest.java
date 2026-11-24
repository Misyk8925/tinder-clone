package com.tinder.profiles.infrastructure.persistence.profile.internal;

import com.tinder.profiles.application.profile.port.in.InternalProfileQuery;
import com.tinder.profiles.infrastructure.cache.SharedProfileSnapshotCache;
import com.tinder.profiles.infrastructure.persistence.photos.PhotoRepository;
import com.tinder.profiles.infrastructure.persistence.photos.SharedPhotoMapper;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.infrastructure.persistence.profile.mapper.SharedProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaInternalProfileQueryAdapterTest {

    @Mock
    private ProfileRepository repo;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private SharedProfileMapper sharedMapper;

    @Mock
    private SharedPhotoMapper sharedPhotoMapper;

    @Mock
    private SharedProfileRowMapper sharedProfileRowMapper;

    @Mock
    private SharedProfileSnapshotCache sharedProfileSnapshotCache;

    private JpaInternalProfileQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaInternalProfileQueryAdapter(
                repo,
                photoRepository,
                sharedMapper,
                sharedPhotoMapper,
                sharedProfileRowMapper,
                sharedProfileSnapshotCache
        );
    }

    @Test
    @DisplayName("Default SearchCriteria excludes viewer when querying repository")
    void defaultSearchExcludesViewer() {
        UUID viewerId = UUID.randomUUID();
        InternalProfileQuery.SearchCriteria criteria =
                new InternalProfileQuery.SearchCriteria(18, 50, "ALL", 100);

        when(repo.existsById(viewerId)).thenReturn(true);
        when(repo.searchSharedProfileRowsByPreferences(viewerId, 18, 50, "ALL", 200, true))
                .thenReturn(List.of());
        when(sharedProfileRowMapper.idsOf(List.of())).thenReturn(List.of());
        when(sharedProfileRowMapper.toDtos(anyList(), anyMap(), anyMap())).thenReturn(List.of());

        adapter.search(viewerId, criteria, 200);

        verify(repo).searchSharedProfileRowsByPreferences(viewerId, 18, 50, "ALL", 200, true);
    }

    @Test
    @DisplayName("SearchCriteria with excludeViewer=false propagates false so shared cache includes viewer")
    void cachePopulateSearchIncludesViewer() {
        UUID viewerId = UUID.randomUUID();
        InternalProfileQuery.SearchCriteria criteria =
                new InternalProfileQuery.SearchCriteria(18, 50, "ALL", 100, false);

        when(repo.existsById(viewerId)).thenReturn(true);
        when(repo.searchSharedProfileRowsByPreferences(viewerId, 18, 50, "ALL", 200, false))
                .thenReturn(List.of());
        when(sharedProfileRowMapper.idsOf(List.of())).thenReturn(List.of());
        when(sharedProfileRowMapper.toDtos(anyList(), anyMap(), anyMap())).thenReturn(List.of());

        adapter.search(viewerId, criteria, 200);

        verify(repo).searchSharedProfileRowsByPreferences(viewerId, 18, 50, "ALL", 200, false);
    }
}
