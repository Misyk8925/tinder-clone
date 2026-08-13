package com.tinder.profiles.application.profile.model;

/** A different run owns the single active backfill slot. */
public class DeckCardProjectionBackfillConflictException extends RuntimeException {

    public DeckCardProjectionBackfillConflictException() {
        super("Another Deck Card projection backfill is RUNNING");
    }
}
