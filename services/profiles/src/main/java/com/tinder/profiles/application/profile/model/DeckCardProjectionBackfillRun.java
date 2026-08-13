package com.tinder.profiles.application.profile.model;

import java.time.Instant;
import java.util.UUID;

/** Operational state of one explicitly triggered projection rebuild. */
public record DeckCardProjectionBackfillRun(
        UUID runId,
        DeckCardProjectionBackfillStatus status,
        UUID lastProfileId,
        long processedCount,
        long expectedCount,
        Instant startedAt,
        Instant updatedAt,
        Instant completedAt,
        String lastError
) {
}
