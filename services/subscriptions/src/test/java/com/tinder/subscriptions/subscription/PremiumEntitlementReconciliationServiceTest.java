package com.tinder.subscriptions.subscription;

import com.tinder.subscriptions.grpc.SubscriptionGrpcClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumEntitlementReconciliationServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionGrpcClient subscriptionGrpcClient;

    @Test
    @org.junit.jupiter.api.DisplayName("Scenario: Given paid subscriptions missed propagation, when reconciliation runs, then every active entitlement is replayed")
    void replaysEveryActiveEntitlementAndContinuesAfterOneFailure() {
        Instant firstExpiry = Instant.parse("2030-02-03T04:05:06Z");
        Instant secondExpiry = Instant.parse("2030-03-04T05:06:07Z");
        BillingSubscription first = subscription("sub-first", "user-first", firstExpiry);
        BillingSubscription second = subscription("sub-second", "user-second", secondExpiry);
        when(subscriptionRepository.findAllByStatusInAndCurrentPeriodEndAfter(any(), any()))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("profiles unavailable"))
                .when(subscriptionGrpcClient).activatePremiumUntil("user-first", firstExpiry);

        new PremiumEntitlementReconciliationService(subscriptionRepository, subscriptionGrpcClient)
                .reconcileActiveEntitlements();

        verify(subscriptionGrpcClient).activatePremiumUntil("user-first", firstExpiry);
        verify(subscriptionGrpcClient).activatePremiumUntil("user-second", secondExpiry);
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(subscriptionRepository).findAllByStatusInAndCurrentPeriodEndAfter(statuses.capture(), any());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder("active", "trialing");
    }

    private static BillingSubscription subscription(String id, String userId, Instant expiry) {
        BillingSubscription subscription = new BillingSubscription();
        subscription.setStripeSubscriptionId(id);
        subscription.setUserId(userId);
        subscription.setStripeCustomerId("customer-" + id);
        subscription.setStatus("active");
        subscription.setCurrentPeriodEnd(expiry);
        return subscription;
    }
}
