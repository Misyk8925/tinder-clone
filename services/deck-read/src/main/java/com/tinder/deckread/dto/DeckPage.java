package com.tinder.deckread.dto;

import java.util.List;

public record DeckPage(
        List<DeckCardDto> items,
        String nextCursor,
        long generation,
        boolean cursorReset,
        DeckState state
) {
    public DeckPage {
        items = List.copyOf(items);
    }
}
