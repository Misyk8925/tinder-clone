package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileApplicationService;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.user.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PremiumExpirationScheduler {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    private final ProfileRepository profileRepository;
    private final ProfileApplicationService profileApplicationService;
    private final KeycloakAdminClient keycloakAdminClient;

    /**
     * Runs every hour and revokes premium status for all profiles
     * whose subscription period has ended.
     */
    @Scheduled(fixedRateString = "${premium.expiration.check-interval-ms:3600000}")
    public void revokeExpiredPremiumSubscriptions() {
        List<Profile> expired = profileRepository
                .findAllByIsPremiumTrueAndPremiumExpiresAtBefore(LocalDateTime.now());

        if (expired.isEmpty()) {
            log.debug("No expired premium subscriptions found");
            return;
        }

        log.info("Found {} expired premium subscription(s) — revoking", expired.size());

        for (Profile profile : expired) {
            String userId = profile.getUserId();
            try {
                // 1. Clear premium flag and expiry date in the DB
                profileApplicationService.updatePremiumStatus(userId, false);

                // 2. Remove the Keycloak role so the JWT no longer contains it
                keycloakAdminClient.removeRealmRole(userId, PREMIUM_ROLE);

                log.info("Premium revoked for user '{}' (expired at {})",
                        userId, profile.getPremiumExpiresAt());
            } catch (Exception e) {
                // Log and continue — the scheduler will retry on the next run
                log.error("Failed to revoke premium for user '{}': {}", userId, e.getMessage(), e);
            }
        }
    }
}

