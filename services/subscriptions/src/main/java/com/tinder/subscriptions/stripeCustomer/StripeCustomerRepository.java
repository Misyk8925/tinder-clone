package com.tinder.subscriptions.stripeCustomer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, String> {

    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);
    Optional<StripeCustomer> findByUserId(String userId);
}
