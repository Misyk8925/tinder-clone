package com.tinder.deckread.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckMaterializationRequest(
        UUID eventId,
        UUID viewerProfileId,
        long requestedRevision,
        MaterializationReason reason,
        String sourceBuildTimestamp,
        Instant requestedAt
) {
}
