package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillRun;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import com.tinder.profiles.application.profile.port.out.DeckCardProjectionBackfillPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Explicit, restartable maintenance job. It is never started from application
 * startup. Every page is delegated to a separate transactional adapter call.
 */
@Service
@RequiredArgsConstructor
public class DeckCardProjectionBackfillService {

    public static final int BACKFILL_PAGE_SIZE = 500;

    private final DeckCardProjectionBackfillPort backfill;

    public DeckCardProjectionBackfillRun startOrResume(UUID runId) {
        DeckCardProjectionBackfillRun run = backfill.startOrResume(runId);
        try {
            while (run.status() == DeckCardProjectionBackfillStatus.RUNNING) {
                run = backfill.enqueueNextPage(runId, BACKFILL_PAGE_SIZE);
            }
            return backfill.refreshStatus(runId).orElse(run);
        } catch (RuntimeException failure) {
            backfill.markFailed(runId, sanitize(failure));
            throw failure;
        }
    }

    public Optional<DeckCardProjectionBackfillRun> status(UUID runId) {
        return backfill.refreshStatus(runId);
    }

    private String sanitize(RuntimeException failure) {
        String category = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return category;
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]", " ");
        return (category + ": " + sanitized).substring(0, Math.min(500, category.length() + 2 + sanitized.length()));
    }
}
