package com.tinder.subscriptions.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StripeEventInboxRepository extends JpaRepository<StripeEventInbox, String> {
    @Query(value = """
            SELECT * FROM stripe_event_inbox
            WHERE (status = 'PENDING' AND next_retry_at <= :now)
               OR (status = 'PROCESSING' AND next_retry_at <= :now)
            ORDER BY stripe_created ASC
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<StripeEventInbox> claimProcessableBatch(@Param("now") Instant now);
}
