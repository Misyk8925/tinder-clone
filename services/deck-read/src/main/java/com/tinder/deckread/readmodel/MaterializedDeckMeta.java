package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckState;

import java.time.Instant;

public record MaterializedDeckMeta(
        long generation,
        long requestedRevision,
        long publishedRevision,
        Instant builtAt,
        DeckState state,
        String sourceBuildTimestamp,
        int readyCount,
        int totalCount,
        boolean unavailable
) {
}
