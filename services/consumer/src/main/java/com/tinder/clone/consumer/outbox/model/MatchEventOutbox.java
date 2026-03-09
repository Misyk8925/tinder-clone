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
        name = "match_event_outbox",
        indexes = {
                @Index(name = "idx_match_outbox_publish_window", columnList = "published_at, dead_lettered_at, next_attempt_at, created_at"),
                @Index(name = "idx_match_outbox_profile1", columnList = "profile1_id"),
                @Index(name = "idx_match_outbox_profile2", columnList = "profile2_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_match_outbox_event_id", columnNames = "event_id")
        }
)
public class MatchEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "profile1_id", nullable = false, updatable = false)
    private UUID profile1Id;

    @Column(name = "profile2_id", nullable = false, updatable = false)
    private UUID profile2Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private MatchOutboxEventType eventType;

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

    public static MatchEventOutbox pending(
            UUID eventId,
            UUID profile1Id,
            UUID profile2Id,
            MatchOutboxEventType eventType,
            String payload,
            Instant now
    ) {
        MatchEventOutbox row = new MatchEventOutbox();
        row.eventId = eventId;
        row.profile1Id = profile1Id;
        row.profile2Id = profile2Id;
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
