package com.tinder.profiles.profile;

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
    private final ProfileApplicationService profileApplicationService;

    /**
     * Runs once a day and permanently purges profiles that were soft-deleted
     * more than 30 days ago (i.e. deletedAt < now - 30 days).
     */
    @Scheduled(fixedRateString = "${profile.cleanup.check-interval-ms:86400000}")
    @Transactional
    public void purgeStaleDeletedProfiles() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(SOFT_DELETE_RETENTION_DAYS);

        List<Profile> stale = profileRepository.findAllByIsDeletedTrueAndDeletedAtBefore(cutoff);

        if (stale.isEmpty()) {
            log.debug("No stale deleted profiles found for purging");
            return;
        }

        List<UUID> ids = stale.stream()
                .map(Profile::getProfileId)
                .toList();

        log.info("Purging {} profile(s) soft-deleted before {}", ids.size(), cutoff);

        try {
            profileApplicationService.deleteMany(ids);
            log.info("Successfully purged {} profile(s)", ids.size());
        } catch (Exception e) {
            log.error("Failed to purge stale deleted profiles: {}", e.getMessage(), e);
        }
    }
}

