package com.tinder.match.match.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_chat_analytics", indexes = {
        @Index(name = "idx_mca_matched_at", columnList = "matched_at"),
        @Index(name = "idx_mca_last_message_at", columnList = "last_message_at"),
        @Index(name = "idx_mca_profile1_last_message_at", columnList = "profile1_id,last_message_at"),
        @Index(name = "idx_mca_profile2_last_message_at", columnList = "profile2_id,last_message_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchChatAnalytics {

    @EmbeddedId
    private MatchChatAnalyticsId id;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private Instant matchedAt;

    @Column(name = "first_message_at")
    private Instant firstMessageAt;

    @Column(name = "first_message_sender_id")
    private UUID firstMessageSenderId;

    @Column(name = "first_reply_at")
    private Instant firstReplyAt;

    @Column(name = "first_reply_latency_ms")
    private Long firstReplyLatencyMs;

    @Builder.Default
    @Column(name = "total_messages", nullable = false)
    private Long totalMessages = 0L;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_sender_id")
    private UUID lastMessageSenderId;

    @Builder.Default
    @Column(name = "active_days", nullable = false)
    private Integer activeDays = 0;

    @Builder.Default
    @Column(name = "audio_duration_ms_total", nullable = false)
    private Long audioDurationMsTotal = 0L;

    @Builder.Default
    @Column(name = "video_duration_ms_total", nullable = false)
    private Long videoDurationMsTotal = 0L;

    @Column(name = "unmatched_at")
    private Instant unmatchedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (matchedAt == null) {
            matchedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        initializeCounters();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        initializeCounters();
    }

    private void initializeCounters() {
        if (totalMessages == null) {
            totalMessages = 0L;
        }

        if (activeDays == null) {
            activeDays = 0;
        }

        if (audioDurationMsTotal == null) {
            audioDurationMsTotal = 0L;
        }
        if (videoDurationMsTotal == null) {
            videoDurationMsTotal = 0L;
        }
    }
}
