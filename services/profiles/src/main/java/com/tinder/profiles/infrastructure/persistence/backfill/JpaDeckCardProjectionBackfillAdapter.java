package com.tinder.profiles.infrastructure.persistence.backfill;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillRun;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillConflictException;
import com.tinder.profiles.application.profile.port.out.DeckCardProjectionBackfillPort;
import com.tinder.profiles.infrastructure.messaging.DeckCardProjectionOutboxService;
import com.tinder.profiles.infrastructure.messaging.outbox.ProfileEventOutboxRepository;
import com.tinder.profiles.infrastructure.messaging.outbox.ProfileOutboxService;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL implementation: page outbox rows and cursor share one transaction. */
@Component
@RequiredArgsConstructor
public class JpaDeckCardProjectionBackfillAdapter implements DeckCardProjectionBackfillPort {

    private static final long BACKFILL_COORDINATOR_LOCK = 1_145_393_995L;

    private final DeckCardProjectionBackfillRunRepository runs;
    private final ProfileRepository profiles;
    private final ProfileEventOutboxRepository profile_event_outbox;
    private final ProfileOutboxService outbox;
    private final DeckCardProjectionOutboxService projections;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public DeckCardProjectionBackfillRun startOrResume(UUID runId) {
        acquireSingletonGate();
        Optional<DeckCardProjectionBackfillRunJpaEntity> existing = runs.findByIdForUpdate(runId);
        if (existing.isPresent()) {
            DeckCardProjectionBackfillRunJpaEntity run = existing.get();
            if (run.getStatus() == DeckCardProjectionBackfillStatus.FAILED) {
                if (runs.existsByStatus(DeckCardProjectionBackfillStatus.RUNNING)) {
                    throw new DeckCardProjectionBackfillConflictException();
                }
                run.resume(Instant.now());
            }
            return toView(run);
        }
        if (runs.existsByStatus(DeckCardProjectionBackfillStatus.RUNNING)) {
            throw new DeckCardProjectionBackfillConflictException();
        }
        return toView(runs.save(DeckCardProjectionBackfillRunJpaEntity.running(
                runId, profiles.count(), Instant.now())));
    }

    private void acquireSingletonGate() {
        // Serializes check/create/resume decisions across replicas. The durable
        // unique active_slot constraint remains the final invariant.
        entityManager.createNativeQuery(
                        "SELECT 1 FROM pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", BACKFILL_COORDINATOR_LOCK)
                .getSingleResult();
    }

    @Override
    @Transactional
    public DeckCardProjectionBackfillRun enqueueNextPage(UUID runId, int pageSize) {
        if (pageSize < 1 || pageSize > 500) {
            throw new IllegalArgumentException("Backfill page size must be between 1 and 500");
        }

        DeckCardProjectionBackfillRunJpaEntity run = runs.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown backfill run: " + runId));
        if (run.getStatus() != DeckCardProjectionBackfillStatus.RUNNING) {
            return toView(refresh(run));
        }

        List<UUID> ids = profiles.findNextProjectionBackfillIds(
                run.getLastProfileId(), PageRequest.of(0, pageSize));
        if (ids.isEmpty()) {
            run.markEnqueued(Instant.now());
            return toView(refresh(run));
        }

        projections.buildBackfillPage(ids, backfillRunId(run)).forEach(event ->
                outbox.enqueueDeckCardProjection(event, run.getRunId()));
        run.pageCommitted(ids.get(ids.size() - 1), ids.size(), Instant.now());
        if (ids.size() < pageSize) {
            run.markEnqueued(Instant.now());
        }
        return toView(refresh(run));
    }

    @Override
    @Transactional
    public Optional<DeckCardProjectionBackfillRun> refreshStatus(UUID runId) {
        return runs.findByIdForUpdate(runId).map(run -> toView(refresh(run)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeckCardProjectionBackfillRun markFailed(UUID runId, String sanitizedError) {
        DeckCardProjectionBackfillRunJpaEntity run = runs.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown backfill run: " + runId));
        run.markFailed(sanitizedError, Instant.now());
        return toView(run);
    }

    private UUID backfillRunId(DeckCardProjectionBackfillRunJpaEntity run) {
        return run.getRunId();
    }

    private DeckCardProjectionBackfillRunJpaEntity refresh(DeckCardProjectionBackfillRunJpaEntity run) {
        if (run.getStatus() != DeckCardProjectionBackfillStatus.ENQUEUED) {
            return run;
        }
        if (profile_event_outbox.countByBackfillRunIdAndDeadLetteredAtIsNotNull(run.getRunId()) > 0) {
            run.markFailed("One or more projection events are dead-lettered", Instant.now());
        } else if (profile_event_outbox
                .countByBackfillRunIdAndPublishedAtIsNullAndDeadLetteredAtIsNull(run.getRunId()) == 0) {
            run.markCompleted(Instant.now());
        }
        return run;
    }

    private DeckCardProjectionBackfillRun toView(DeckCardProjectionBackfillRunJpaEntity run) {
        return new DeckCardProjectionBackfillRun(
                run.getRunId(), run.getStatus(), run.getLastProfileId(),
                run.getProcessedCount(), run.getExpectedCount(), run.getStartedAt(),
                run.getUpdatedAt(), run.getCompletedAt(), run.getLastError());
    }
}
