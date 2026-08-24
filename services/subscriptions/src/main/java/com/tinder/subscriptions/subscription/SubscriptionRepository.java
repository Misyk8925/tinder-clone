package com.tinder.subscriptions.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<BillingSubscription, String> {
    List<BillingSubscription> findAllByStatusInAndCurrentPeriodEndAfter(
            Collection<String> statuses,
            Instant now
    );
}
