package com.tinder.deckread.readmodel;

import java.util.List;
import java.util.UUID;

public record DeckSnapshot(
        DeckSnapshotMeta meta,
        List<UUID> fresh,
        List<UUID> repeat
) {
    public DeckSnapshot {
        fresh = List.copyOf(fresh);
        repeat = List.copyOf(repeat);
    }
}
