package com.tinder.match.match;

import java.time.Instant;
import java.util.UUID;

public record NewMatchEvent(
        UUID eventId,
        UUID profile1Id,
        UUID profile2Id,
        Instant matchedAt
) {
}
