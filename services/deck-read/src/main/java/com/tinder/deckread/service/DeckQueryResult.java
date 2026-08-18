package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckPage;

public sealed interface DeckQueryResult {
    record Page(DeckPage value) implements DeckQueryResult {}
    record Building() implements DeckQueryResult {}
    record Failure(int status, String code, String title, String detail) implements DeckQueryResult {}
}
