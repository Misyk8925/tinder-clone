package com.tinder.profiles.infrastructure.messaging;

import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.profiles.infrastructure.persistence.photos.PhotoRepository;
import com.tinder.profiles.infrastructure.persistence.preferences.Preferences;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("acceptance")
@DisplayName("Feature: Profiles backfill restores active cards and deleted tombstones")
class DeckCardProjectionFactoryTest {

    @Mock
    ProfileRepository profiles;

    @Mock
    PhotoRepository photos;

    @Test
    @DisplayName("Scenario: Given active and deleted profiles, when a backfill page is built, then their operations are UPSERT and DELETE")
    void backfillChoosesOperationFromCurrentProfileState() {
        // Given
        UUID activeId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        List<UUID> ids = List.of(activeId, deletedId);
        when(profiles.findAllById(ids)).thenReturn(List.of(
                profile(activeId, "active-user", false),
                profile(deletedId, "deleted-user", true)));
        when(photos.findAllByProfile_ProfileIdIn(ids)).thenReturn(List.of());

        // When
        var events = new DeckCardProjectionFactory(profiles, photos).buildBatch(
                ids, ProfileProjectionOperation.UPSERT, ProjectionSource.BACKFILL, UUID.randomUUID());

        // Then
        assertThat(events)
                .extracting(event -> event.operation())
                .containsExactly(ProfileProjectionOperation.UPSERT, ProfileProjectionOperation.DELETE);
    }

    private ProfileJpaEntity profile(UUID profileId, String userId, boolean deleted) {
        return ProfileJpaEntity.builder()
                .profileId(profileId)
                .userId(userId)
                .version(7L)
                .name("Profile")
                .age(30)
                .gender("ALL")
                .city("Vienna")
                .isActive(!deleted)
                .isDeleted(deleted)
                .preferences(Preferences.builder()
                        .minAge(18)
                        .maxAge(99)
                        .gender("ALL")
                        .maxRange(50)
                        .build())
                .hobbies(List.of())
                .build();
    }
}
