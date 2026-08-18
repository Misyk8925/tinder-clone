package com.tinder.deckread.messaging;

public enum MaterializationReason {
    DECK_BUILT,
    SWIPE_SAVED,
    MATCH_CREATED,
    PROFILE_CHANGED,
    PROFILE_DELETED,
    API_MISS,
    API_STALE,
    RECONCILIATION
}
