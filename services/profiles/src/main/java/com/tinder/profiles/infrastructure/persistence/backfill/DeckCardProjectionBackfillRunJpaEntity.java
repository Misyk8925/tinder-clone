package com.tinder.profiles.infrastructure.persistence.backfill;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "deck_card_projection_backfill_run",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_deck_card_projection_backfill_active_slot",
                columnNames = "active_slot")
)
public class DeckCardProjectionBackfillRunJpaEntity {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeckCardProjectionBackfillStatus status;

    @Column(name = "active_slot", unique = true)
    private Integer activeSlot;

    @Column(name = "last_profile_id")
    private UUID lastProfileId;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "expected_count", nullable = false)
    private long expectedCount;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public static DeckCardProjectionBackfillRunJpaEntity running(
            UUID runId,
            long expectedCount,
            Instant now
    ) {
        DeckCardProjectionBackfillRunJpaEntity run = new DeckCardProjectionBackfillRunJpaEntity();
        run.runId = runId;
        run.status = DeckCardProjectionBackfillStatus.RUNNING;
        run.activeSlot = 1;
        run.expectedCount = expectedCount;
        run.startedAt = now;
        run.updatedAt = now;
        return run;
    }

    public void pageCommitted(UUID lastProfileId, int pageCount, Instant now) {
        this.lastProfileId = lastProfileId;
        this.processedCount += pageCount;
        this.updatedAt = now;
    }

    public void markEnqueued(Instant now) {
        this.status = DeckCardProjectionBackfillStatus.ENQUEUED;
        this.activeSlot = null;
        this.updatedAt = now;
    }

    public void markCompleted(Instant now) {
        this.status = DeckCardProjectionBackfillStatus.COMPLETED;
        this.activeSlot = null;
        this.updatedAt = now;
        this.completedAt = now;
        this.lastError = null;
    }

    public void markFailed(String error, Instant now) {
        this.status = DeckCardProjectionBackfillStatus.FAILED;
        this.activeSlot = null;
        this.updatedAt = now;
        this.completedAt = now;
        this.lastError = error;
    }

    public void resume(Instant now) {
        this.status = DeckCardProjectionBackfillStatus.RUNNING;
        this.activeSlot = 1;
        this.updatedAt = now;
        this.completedAt = null;
        this.lastError = null;
    }
}
