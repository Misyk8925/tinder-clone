package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable hand-off emitted after Deck has installed a complete Redis snapshot.
 * Records are keyed by {@code viewerProfileId} on {@code deck.built.v1}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeckBuiltEventV1(
        @NotNull UUID eventId,
        @NotNull UUID viewerProfileId,
        @NotBlank String sourceBuildTimestamp,
        @Min(0) int candidateCount,
        @NotNull Instant occurredAt
) {
}
