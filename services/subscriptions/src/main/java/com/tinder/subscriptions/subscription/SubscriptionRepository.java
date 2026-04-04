package com.tinder.subscriptions.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<BillingSubscription, String> {
}
