package com.tinder.contracts.deck;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckRedisKeysTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void exposesTheFrozenDeckKeySchema() {
        assertEquals("deck:" + ID, DeckRedisKeys.deck(ID));
        assertEquals("deck:", DeckRedisKeys.PRIMARY_DECK_PREFIX);
        assertEquals("deck:*", DeckRedisKeys.PRIMARY_DECK_SCAN_PATTERN);
        assertEquals("deck:build:ts:" + ID, DeckRedisKeys.buildTimestamp(ID));
        assertEquals("deck:stale:" + ID, DeckRedisKeys.stale(ID));
        assertEquals("deck:lock:" + ID, DeckRedisKeys.lock(ID));
        assertEquals("deck:contains:" + ID, DeckRedisKeys.contains(ID));
        assertEquals("deck:profile:invalidated-at:" + ID, DeckRedisKeys.invalidatedAt(ID));
        assertEquals("deck:profile:deleted", DeckRedisKeys.DELETED_PROFILES);
        assertEquals("deck:recent:viewers", DeckRedisKeys.RECENT_VIEWERS);
    }

    @Test
    void recognizesOnlyPrimaryDeckKeys() {
        assertTrue(DeckRedisKeys.PRIMARY_DECK_KEY.matcher(DeckRedisKeys.deck(ID)).matches());
        assertFalse(DeckRedisKeys.PRIMARY_DECK_KEY.matcher(DeckRedisKeys.buildTimestamp(ID)).matches());
        assertFalse(DeckRedisKeys.PRIMARY_DECK_KEY.matcher(DeckRedisKeys.stale(ID)).matches());
    }

    @Test
    void preferenceKeysAreLocaleIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("prefs:18:35:BI", DeckRedisKeys.preferences(18, 35, "bi"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
