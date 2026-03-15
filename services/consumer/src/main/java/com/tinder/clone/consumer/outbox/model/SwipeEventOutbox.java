package com.tinder.clone.consumer.outbox.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "swipe_event_outbox",
        indexes = {
                @Index(name = "idx_swipe_outbox_publish_window", columnList = "published_at, dead_lettered_at, next_attempt_at, created_at"),
                @Index(name = "idx_swipe_outbox_swiper", columnList = "swiper_id"),
                @Index(name = "idx_swipe_outbox_swiped", columnList = "swiped_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_swipe_outbox_event_id", columnNames = "event_id")
        }
)
public class SwipeEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "swiper_id", nullable = false, updatable = false)
    private UUID swiperId;

    @Column(name = "swiped_id", nullable = false, updatable = false)
    private UUID swipedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private SwipeOutboxEventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static SwipeEventOutbox pending(
            UUID eventId,
            UUID swiperId,
            UUID swipedId,
            SwipeOutboxEventType eventType,
            String payload,
            Instant now
    ) {
        SwipeEventOutbox row = new SwipeEventOutbox();
        row.eventId = eventId;
        row.swiperId = swiperId;
        row.swipedId = swipedId;
        row.eventType = eventType;
        row.payload = payload;
        row.retryCount = 0;
        row.nextAttemptAt = now;
        row.createdAt = now;
        return row;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
        this.deadLetteredAt = null;
        this.lastError = null;
    }

    public void scheduleRetry(Instant nextAttemptAt, String errorMessage) {
        this.retryCount += 1;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = errorMessage;
    }

    public void markDeadLettered(Instant now, String errorMessage) {
        this.retryCount += 1;
        this.deadLetteredAt = now;
        this.lastError = errorMessage;
    }
}
