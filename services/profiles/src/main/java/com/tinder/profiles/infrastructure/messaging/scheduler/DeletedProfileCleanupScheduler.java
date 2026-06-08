package com.tinder.profiles.infrastructure.messaging.scheduler;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;
import com.tinder.profiles.application.profile.usecase.DeleteProfilesService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeletedProfileCleanupScheduler {

    private static final int SOFT_DELETE_RETENTION_DAYS = 30;

    private final ProfileRepository profileRepository;
    private final DeleteProfilesService deleteProfilesUseCase;

    /**
     * Runs once a day and permanently purges profiles that were soft-deleted
     * more than 30 days ago (i.e. deletedAt < now - 30 days).
     */
    @Scheduled(fixedRateString = "${profile.cleanup.check-interval-ms:86400000}")
    @Transactional
    public void purgeStaleDeletedProfiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(SOFT_DELETE_RETENTION_DAYS);

        List<ProfileJpaEntity> stale = profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(cutoff);

        if (stale.isEmpty()) {
            log.debug("No stale deleted profiles found for purging");
            return;
        }

        List<UUID> ids = stale.stream()
                .map(ProfileJpaEntity::getProfileId)
                .toList();

        log.info("Purging {} profile(s) soft-deleted before {}", ids.size(), cutoff);

        try {
            deleteProfilesUseCase.handle(new DeleteProfilesCommand(ids));
            log.info("Successfully purged {} profile(s)", ids.size());
        } catch (Exception e) {
            log.error("Failed to purge stale deleted profiles: {}", e.getMessage(), e);
        }
    }
}

