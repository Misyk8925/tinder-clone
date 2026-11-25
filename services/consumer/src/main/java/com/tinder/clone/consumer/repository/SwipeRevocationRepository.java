package com.tinder.clone.consumer.repository;

import com.tinder.clone.consumer.model.SwipeRevocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SwipeRevocationRepository extends JpaRepository<SwipeRevocation, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO swipe_revocations (
            event_id, revoked_swipe_event_id, profile1_id, profile2_id,
            original_decision, swiped_at, revoked_at, status, processed_at
        ) VALUES (
            :eventId, :revokedSwipeEventId, :profile1Id, :profile2Id,
            :originalDecision, :swipedAt, :revokedAt, :status, :processedAt
        )
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(
            @Param("eventId") UUID eventId,
            @Param("revokedSwipeEventId") UUID revokedSwipeEventId,
            @Param("profile1Id") UUID profile1Id,
            @Param("profile2Id") UUID profile2Id,
            @Param("originalDecision") boolean originalDecision,
            @Param("swipedAt") Instant swipedAt,
            @Param("revokedAt") Instant revokedAt,
            @Param("status") String status,
            @Param("processedAt") Instant processedAt
    );

    boolean existsByRevokedSwipeEventId(UUID revokedSwipeEventId);
}
