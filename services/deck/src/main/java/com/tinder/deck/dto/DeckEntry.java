package com.tinder.deck.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Represents a single entry in the cached deck stored in Redis.
 * isSwiped=true means the viewer has already swiped on this profile
 * but the deck has not been rebuilt by the scheduler yet.
 */
public record DeckEntry(UUID profileId, boolean isSwiped) {

    @JsonCreator
    public DeckEntry(
            @JsonProperty("profileId") UUID profileId,
            @JsonProperty("isSwiped") boolean isSwiped
    ) {
        this.profileId = profileId;
        this.isSwiped = isSwiped;
    }

    public static DeckEntry fresh(UUID profileId) {
        return new DeckEntry(profileId, false);
    }

    public DeckEntry withSwiped() {
        return new DeckEntry(this.profileId, true);
    }
}
