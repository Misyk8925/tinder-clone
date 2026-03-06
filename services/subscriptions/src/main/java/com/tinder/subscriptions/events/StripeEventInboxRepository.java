package com.tinder.subscriptions.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface StripeEventInboxRepository extends JpaRepository<StripeEventInbox, String> {
    List<StripeEventInbox> findTop50ByStatusAndNextRetryAtBeforeOrderByStripeCreatedAsc(StripeEventInbox.Status status, Instant now);
}