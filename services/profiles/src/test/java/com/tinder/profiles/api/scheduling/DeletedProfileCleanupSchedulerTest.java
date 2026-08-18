package com.tinder.profiles.api.scheduling;

import com.tinder.profiles.application.profile.usecase.PurgeSoftDeletedProfilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * The retention window and the purge itself live in
 * {@link PurgeSoftDeletedProfilesService}; this trigger only has to be resilient.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeletedProfileCleanupScheduler")
class DeletedProfileCleanupSchedulerTest {

    @Mock private PurgeSoftDeletedProfilesService purgeSoftDeletedProfiles;

    private DeletedProfileCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DeletedProfileCleanupScheduler(purgeSoftDeletedProfiles);
    }

    @Test
    @DisplayName("delegates the purge to the use case")
    void delegatesToUseCase() {
        given(purgeSoftDeletedProfiles.handle()).willReturn(3);

        scheduler.purgeStaleDeletedProfiles();

        verify(purgeSoftDeletedProfiles).handle();
    }

    @Test
    @DisplayName("swallows failures so the next run can retry")
    void purgeFailureDoesNotPropagate() {
        willThrow(new RuntimeException("DB constraint violation"))
                .given(purgeSoftDeletedProfiles).handle();

        scheduler.purgeStaleDeletedProfiles();
    }
}
