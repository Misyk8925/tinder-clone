package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckState;

import java.time.Instant;

public record DeckSnapshotMeta(
        long generation,
        Instant builtAt,
        DeckState state,
        String sourceBuildTimestamp,
        Instant refreshStartedAt,
        int failureCount,
        boolean unavailable
) {
}
