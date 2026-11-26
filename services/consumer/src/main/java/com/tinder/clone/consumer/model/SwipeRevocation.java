package com.tinder.clone.consumer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "swipe_revocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwipeRevocation {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "revoked_swipe_event_id", nullable = false, updatable = false)
    private UUID revokedSwipeEventId;

    @Column(name = "profile1_id", nullable = false, updatable = false)
    private UUID profile1Id;

    @Column(name = "profile2_id", nullable = false, updatable = false)
    private UUID profile2Id;

    @Column(name = "original_decision", nullable = false, updatable = false)
    private boolean originalDecision;

    @Column(name = "swiped_at", nullable = false, updatable = false)
    private Instant swipedAt;

    @Column(name = "revoked_at", nullable = false, updatable = false)
    private Instant revokedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
