package com.tinder.subscriptions.subscription;

import com.tinder.subscriptions.grpc.SubscriptionGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Repairs premium propagation when Profiles was unavailable while Stripe state
 * was synchronized. Granting the realm role and writing the profile entitlement
 * are idempotent, so replaying active paid-through records is safe.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PremiumEntitlementReconciliationService {

    private static final Set<String> ENTITLED_STATUSES = Set.of("active", "trialing");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionGrpcClient subscriptionGrpcClient;

    @Scheduled(
            initialDelayString = "${billing.entitlement-reconciliation.initial-delay-ms:15000}",
            fixedDelayString = "${billing.entitlement-reconciliation.fixed-delay-ms:300000}"
    )
    public void reconcileActiveEntitlements() {
        var activeSubscriptions = subscriptionRepository
                .findAllByStatusInAndCurrentPeriodEndAfter(ENTITLED_STATUSES, Instant.now());

        if (activeSubscriptions.isEmpty()) {
            return;
        }

        log.info("Reconciling {} active premium entitlement(s)", activeSubscriptions.size());
        for (BillingSubscription subscription : activeSubscriptions) {
            try {
                subscriptionGrpcClient.activatePremiumUntil(
                        subscription.getUserId(),
                        subscription.getCurrentPeriodEnd()
                );
            } catch (RuntimeException error) {
                log.warn(
                        "Premium entitlement reconciliation failed for subscription {}: {}",
                        subscription.getStripeSubscriptionId(),
                        error.getMessage()
                );
            }
        }
    }
}
