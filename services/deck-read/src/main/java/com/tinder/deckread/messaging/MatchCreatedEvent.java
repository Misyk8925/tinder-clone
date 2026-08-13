package com.tinder.deckread.messaging;

import java.time.Instant;

public record MatchCreatedEvent(
        String eventId,
        String profile1Id,
        String profile2Id,
        Instant createdAt
) {
}
