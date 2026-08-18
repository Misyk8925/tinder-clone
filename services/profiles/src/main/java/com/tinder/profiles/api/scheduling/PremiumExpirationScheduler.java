package com.tinder.profiles.api.scheduling;

import com.tinder.profiles.application.profile.usecase.PremiumMembershipService;
import com.tinder.profiles.application.profile.usecase.PremiumMembershipService.LapsedMembership;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Time-driven inbound adapter: asks the application layer which memberships have
 * lapsed and revokes them one by one.
 *
 * <p>The adapter owns only scheduling and observability — which profiles count as
 * expired is decided by {@link PremiumMembershipService}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PremiumExpirationScheduler {

    private final PremiumMembershipService premiumMembership;
    private final Tracer tracer;   // injected by Micrometer Tracing auto-configuration

    /**
     * Runs every hour and revokes premium for every lapsed membership.
     *
     * <p>A root span is created manually because scheduled tasks have no HTTP
     * context, so Micrometer Tracing would not generate a traceId otherwise. All
     * log lines inside this method share that traceId, making it easy to find the
     * full scheduler run in ELK / Zipkin.
     */
    @Scheduled(fixedRateString = "${premium.expiration.check-interval-ms:3600000}")
    public void revokeExpiredPremiumSubscriptions() {
        Span rootSpan = tracer.nextSpan().name("premium-expiration-check").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(rootSpan)) {

            List<LapsedMembership> lapsed = premiumMembership.findLapsed();
            if (lapsed.isEmpty()) {
                log.debug("No expired premium subscriptions found");
                return;
            }

            log.info("Found {} expired premium subscription(s) — revoking", lapsed.size());
            lapsed.forEach(this::revoke);

        } finally {
            rootSpan.end();
        }
    }

    /** Each revocation gets its own span so Zipkin shows them separately. */
    private void revoke(LapsedMembership membership) {
        Span span = tracer.nextSpan().name("revoke-premium").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            MDC.put("userId", membership.userId());

            premiumMembership.revoke(membership.userId());

            log.info("Premium revoked for user '{}' (expired at {})",
                    membership.userId(), membership.expiredAt());
        } catch (Exception e) {
            // Tag the span as failed and continue — the next run retries this user.
            span.tag("error", e.getMessage());
            log.error("Failed to revoke premium for user '{}': {}",
                    membership.userId(), e.getMessage(), e);
        } finally {
            MDC.remove("userId");
            span.end();
        }
    }
}
