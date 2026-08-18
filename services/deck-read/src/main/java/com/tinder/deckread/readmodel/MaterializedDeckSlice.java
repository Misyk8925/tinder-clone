package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;

import java.time.Instant;
import java.util.List;

public record MaterializedDeckSlice(
        List<DeckCardDto> cards,
        long generation,
        boolean cursorReset,
        int nextPosition,
        int totalCount,
        DeckState state,
        Instant builtAt,
        String sourceBuildTimestamp,
        boolean unavailable
) {
    public MaterializedDeckSlice {
        cards = List.copyOf(cards);
    }
}
