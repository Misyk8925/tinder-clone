package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * A single entry in the cached deck stored in Redis as a sorted-set member.
 *
 * <p>This is the on-the-wire contract between the deck (write) service, which serializes
 * entries into the sorted set {@code deck:{viewerId}}, and the deck-read service, which
 * deserializes them. The JSON shape is exactly:
 * {@code {"profileId":"<uuid>","isSwiped":false}} — field names must not change.
 *
 * <p>{@code isSwiped=true} means the viewer has already swiped on this profile but the
 * deck has not yet been rebuilt by the scheduler; readers must filter these out.
 *
 * @param profileId the candidate profile's UUID
 * @param isSwiped  whether the viewer has already swiped this profile
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
