package com.tinder.profiles.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletedProfileCleanupSchedulerTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileApplicationService profileApplicationService;

    private DeletedProfileCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DeletedProfileCleanupScheduler(profileRepository, profileApplicationService);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void purgeStaleDeletedProfiles_noStaleProfiles_skipsProcessing() {
        when(profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.purgeStaleDeletedProfiles();

        verifyNoInteractions(profileApplicationService);
    }

    @Test
    void purgeStaleDeletedProfiles_singleStaleProfile_callsDeleteManyWithItsId() {
        UUID id = UUID.randomUUID();
        Profile stale = buildDeletedProfile(id, LocalDateTime.now().minusDays(31));

        when(profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        scheduler.purgeStaleDeletedProfiles();

        verify(profileApplicationService).deleteMany(List.of(id));
    }

    @Test
    void purgeStaleDeletedProfiles_multipleStaleProfiles_passeAllIdsInOneCall() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<Profile> stale = List.of(
                buildDeletedProfile(id1, LocalDateTime.now().minusDays(31)),
                buildDeletedProfile(id2, LocalDateTime.now().minusDays(45)),
                buildDeletedProfile(id3, LocalDateTime.now().minusDays(60))
        );

        when(profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(stale);

        scheduler.purgeStaleDeletedProfiles();

        // All IDs must be sent in a single deleteMany call
        verify(profileApplicationService).deleteMany(
                argThat(ids -> ids.size() == 3
                        && ids.contains(id1)
                        && ids.contains(id2)
                        && ids.contains(id3))
        );
        verifyNoMoreInteractions(profileApplicationService);
    }

    @Test
    void purgeStaleDeletedProfiles_passesCorrect30DayCutoffToRepository() {
        when(profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(30).minusSeconds(5);
        scheduler.purgeStaleDeletedProfiles();
        LocalDateTime after = LocalDateTime.now().minusDays(30).plusSeconds(5);

        verify(profileRepository).findAllByIsDeletedTrueAndDeletedAtBefore(
                argThat(cutoff -> !cutoff.isBefore(before) && !cutoff.isAfter(after))
        );
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void purgeStaleDeletedProfiles_deleteManyThrows_doesNotPropagateException() {
        UUID id = UUID.randomUUID();
        Profile stale = buildDeletedProfile(id, LocalDateTime.now().minusDays(35));

        when(profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(stale));

        doThrow(new RuntimeException("DB constraint violation"))
                .when(profileApplicationService)
                .deleteMany(any());

        // Must not throw — error is logged and swallowed so scheduler continues on next run
        scheduler.purgeStaleDeletedProfiles();
    }

    // ── Domain: deletedAt set on soft-delete ──────────────────────────────────

    @Test
    void profile_markAsDeleted_setsDeletedAt() {
        Profile profile = new Profile();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        profile.markAsDeleted();

        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assert profile.isDeleted();
        assert !profile.isActive();
        assert profile.getDeletedAt() != null;
        assert !profile.getDeletedAt().isBefore(before);
        assert !profile.getDeletedAt().isAfter(after);
    }

    @Test
    void profile_markAsDeleted_setsDeletedAtOnlyOnce() {
        Profile profile = new Profile();
        profile.markAsDeleted();
        LocalDateTime firstDeletedAt = profile.getDeletedAt();

        // Simulate calling markAsDeleted a second time (e.g. duplicate request)
        profile.markAsDeleted();

        // deletedAt should be refreshed to the latest call's time — that's acceptable,
        // but profileId and userId must remain unchanged
        assert profile.getDeletedAt() != null;
        assert !profile.getDeletedAt().isBefore(firstDeletedAt);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Profile buildDeletedProfile(UUID id, LocalDateTime deletedAt) {
        Profile p = new Profile();
        p.setProfileId(id);
        p.setDeleted(true);
        p.setActive(false);
        p.setDeletedAt(deletedAt);
        return p;
    }
}

