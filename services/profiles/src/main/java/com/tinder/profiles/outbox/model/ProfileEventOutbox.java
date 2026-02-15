package com.tinder.profiles.outbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "profile_event_outbox",
        indexes = {
                @Index(name = "idx_outbox_publish_window", columnList = "published_at, dead_lettered_at, next_attempt_at, created_at"),
                @Index(name = "idx_outbox_profile", columnList = "profile_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id")
        }
)
public class ProfileEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private ProfileOutboxEventType eventType;

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

    public static ProfileEventOutbox pending(
            UUID eventId,
            UUID profileId,
            ProfileOutboxEventType eventType,
            String payload,
            Instant now
    ) {
        ProfileEventOutbox row = new ProfileEventOutbox();
        row.eventId = eventId;
        row.profileId = profileId;
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
