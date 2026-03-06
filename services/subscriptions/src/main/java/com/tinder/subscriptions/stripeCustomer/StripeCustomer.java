package com.tinder.subscriptions.stripeCustomer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stripe_customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeCustomer {

    @Id
    private String id;

    private String userId;

    private String stripeCustomerId;

    private Boolean livemode;

    private Instant createdAt;

    private Instant updatedAt;

}
