package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileApplicationService;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.user.KeycloakAdminClient;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final Tracer tracer;   // injected by Micrometer Tracing auto-configuration

    /**
     * Runs every hour and revokes premium status for all profiles
     * whose subscription period has ended.
     *
     * A root span is created manually because scheduled tasks have no HTTP context,
     * so Micrometer Tracing would not generate a traceId otherwise.
     * All log lines inside this method will share the same traceId,
     * making it easy to find the full scheduler run in ELK / Zipkin.
     */
    @Scheduled(fixedRateString = "${premium.expiration.check-interval-ms:3600000}")
    public void revokeExpiredPremiumSubscriptions() {
        // Start a root span so every log line below gets a traceId
        Span rootSpan = tracer.nextSpan().name("premium-expiration-check").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(rootSpan)) {

            List<Profile> expired = profileRepository
                    .findAllByIsPremiumTrueAndPremiumExpiresAtBefore(LocalDateTime.now());

            if (expired.isEmpty()) {
                log.debug("No expired premium subscriptions found");
                return;
            }

            log.info("Found {} expired premium subscription(s) — revoking", expired.size());

            for (Profile profile : expired) {
                String userId = profile.getUserId();
                // Create a child span per user so Zipkin shows each revocation separately
                Span childSpan = tracer.nextSpan().name("revoke-premium").start();
                try (Tracer.SpanInScope ignored2 = tracer.withSpan(childSpan)) {
                    // Put userId into MDC so log lines inside the loop are searchable by user
                    MDC.put("userId", userId);

                    // 1. Clear premium flag and expiry date in the DB
                    profileApplicationService.updatePremiumStatus(userId, false);

                    // 2. Remove the Keycloak role so the JWT no longer contains it
                    keycloakAdminClient.removeRealmRole(userId, PREMIUM_ROLE);

                    log.info("Premium revoked for user '{}' (expired at {})",
                            userId, profile.getPremiumExpiresAt());
                } catch (Exception e) {
                    // Tag the span as failed so Zipkin marks it red
                    childSpan.tag("error", e.getMessage());
                    // Log and continue — the scheduler will retry on the next run
                    log.error("Failed to revoke premium for user '{}': {}", userId, e.getMessage(), e);
                } finally {
                    MDC.remove("userId");
                    childSpan.end();
                }
            }
        } finally {
            rootSpan.end();
        }
    }
}


