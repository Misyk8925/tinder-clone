package com.tinder.deckread.redis;

import java.util.List;
import java.util.UUID;

/** Stable import from the existing Deck Redis hand-off. */
public record SourceDeckSnapshot(List<UUID> orderedProfileIds, String buildTimestamp) {
    public SourceDeckSnapshot {
        orderedProfileIds = List.copyOf(orderedProfileIds);
    }
}
