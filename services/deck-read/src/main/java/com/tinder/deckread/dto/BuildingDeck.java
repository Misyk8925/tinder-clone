package com.tinder.deckread.dto;

public record BuildingDeck(String state, int retryAfterSeconds) {
    public static BuildingDeck polling() {
        return new BuildingDeck("BUILDING", 2);
    }
}
