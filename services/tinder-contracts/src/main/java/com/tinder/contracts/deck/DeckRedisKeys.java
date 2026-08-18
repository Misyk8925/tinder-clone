package com.tinder.contracts.deck;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Redis key schema shared by the deck writer and deck-read service.
 *
 * <p>The cached deck is an intentional CQRS hand-off through Redis. Keeping every key format in
 * this dependency makes writer/reader drift a compile-time concern instead of a production-only
 * cache miss.
 */
public final class DeckRedisKeys {

    public static final String PRIMARY_DECK_PREFIX = "deck:";
    public static final String PRIMARY_DECK_SCAN_PATTERN = PRIMARY_DECK_PREFIX + "*";
    public static final String DELETED_PROFILES = "deck:profile:deleted";
    public static final String RECENT_VIEWERS = "deck:recent:viewers";
    public static final Pattern PRIMARY_DECK_KEY =
            Pattern.compile("^deck:([0-9a-fA-F-]{36})$");

    private DeckRedisKeys() {
    }

    public static String deck(UUID viewerId) {
        return PRIMARY_DECK_PREFIX + viewerId;
    }

    public static String buildTimestamp(UUID viewerId) {
        return "deck:build:ts:" + viewerId;
    }

    public static String stale(UUID viewerId) {
        return "deck:stale:" + viewerId;
    }

    public static String lock(UUID viewerId) {
        return "deck:lock:" + viewerId;
    }

    public static String contains(UUID profileId) {
        return "deck:contains:" + profileId;
    }

    public static String invalidatedAt(UUID profileId) {
        return "deck:profile:invalidated-at:" + profileId;
    }

    public static String preferences(int minAge, int maxAge, String gender) {
        return "prefs:%d:%d:%s".formatted(
                minAge,
                maxAge,
                gender.toUpperCase(Locale.ROOT)
        );
    }
}
