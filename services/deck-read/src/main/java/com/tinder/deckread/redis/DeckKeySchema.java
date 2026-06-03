package com.tinder.deckread.redis;

import java.util.UUID;

/**
 * Literal Redis key formats for the cached deck.
 *
 * <p>IMPORTANT: these MUST stay byte-for-byte identical to the keys written by the deck
 * (write) service in {@code com.tinder.deck.service.DeckCache}. They are duplicated here
 * intentionally rather than shared, mirroring how the profiles service's
 * {@code DeckCacheReader} keeps its own copy. If the write side changes a key format,
 * change it here too.
 */
public final class DeckKeySchema {

    private DeckKeySchema() {
    }

    /** Sorted set: members are {@code DeckEntry} JSON, score is the compatibility score. */
    public static String deck(UUID viewerId) {
        return "deck:" + viewerId;
    }

    /** String: epoch millis of the last successful deck build for this viewer. */
    public static String buildTimestamp(UUID viewerId) {
        return "deck:build:ts:" + viewerId;
    }

    /** Set of profileIds that have been deleted globally. */
    public static final String DELETED_PROFILES = "deck:profile:deleted";

    /** String: epoch millis at which a profile was globally invalidated. */
    public static String invalidatedAt(UUID profileId) {
        return "deck:profile:invalidated-at:" + profileId;
    }
}
