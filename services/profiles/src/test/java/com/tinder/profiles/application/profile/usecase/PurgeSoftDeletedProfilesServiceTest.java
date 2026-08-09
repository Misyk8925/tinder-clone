package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.application.profile.support.ProfileRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurgeSoftDeletedProfilesService")
class PurgeSoftDeletedProfilesServiceTest {

    private static final int RETENTION_DAYS = 30;

    @Mock private ProfileRepositoryPort profiles;
    @Mock private DeleteProfilesService deleteProfiles;

    private PurgeSoftDeletedProfilesService service;

    @BeforeEach
    void setUp() {
        service = new PurgeSoftDeletedProfilesService(
                profiles, deleteProfiles, new ProfileRetentionPolicy(RETENTION_DAYS));
    }

    @Test
    @DisplayName("nothing stale means no delete call")
    void noStaleProfilesSkipsProcessing() {
        given(profiles.findSoftDeletedBefore(any())).willReturn(List.of());

        then(service.handle()).isZero();
        verifyNoInteractions(deleteProfiles);
    }

    @Test
    @DisplayName("purges all stale ids in a single batch")
    void purgesAllStaleIdsInOneCall() {
        List<UUID> stale = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(profiles.findSoftDeletedBefore(any())).willReturn(stale);

        then(service.handle()).isEqualTo(3);
        verify(deleteProfiles).handle(new DeleteProfilesCommand(stale));
    }

    @Test
    @DisplayName("asks for profiles deleted before the retention cutoff")
    void appliesTheRetentionWindow() {
        given(profiles.findSoftDeletedBefore(any())).willReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(RETENTION_DAYS).minusSeconds(5);
        service.handle();
        LocalDateTime after = LocalDateTime.now().minusDays(RETENTION_DAYS).plusSeconds(5);

        verify(profiles).findSoftDeletedBefore(
                argThat(cutoff -> !cutoff.isBefore(before) && !cutoff.isAfter(after)));
    }
}
