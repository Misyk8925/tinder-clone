package com.tinder.profiles.api.scheduling;

import com.tinder.profiles.application.profile.usecase.PurgeSoftDeletedProfilesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Time-driven inbound adapter that triggers the purge of soft-deleted profiles.
 * The retention window itself belongs to
 * {@link PurgeSoftDeletedProfilesService} / {@code ProfileRetentionPolicy}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeletedProfileCleanupScheduler {

    private final PurgeSoftDeletedProfilesService purgeSoftDeletedProfiles;

    @Scheduled(fixedRateString = "${profile.cleanup.check-interval-ms:86400000}")
    public void purgeStaleDeletedProfiles() {
        try {
            int purged = purgeSoftDeletedProfiles.handle();
            if (purged > 0) {
                log.info("Successfully purged {} profile(s)", purged);
            }
        } catch (Exception e) {
            log.error("Failed to purge stale deleted profiles: {}", e.getMessage(), e);
        }
    }
}
