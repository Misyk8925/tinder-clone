package com.tinder.subscriptions.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "billing_subscriptions")
@Getter
@Setter
public class BillingSubscription {
    @Id
    private String stripeSubscriptionId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String stripeCustomerId;

    private String priceId;
    private String status; // active, trialing, past_due, canceled, unpaid, ...
    private Instant currentPeriodEnd;
    private boolean cancelAtPeriodEnd;

    private Long lastStripeEventCreated; // protect against out-of-order events
    private Instant updatedAt;
}
