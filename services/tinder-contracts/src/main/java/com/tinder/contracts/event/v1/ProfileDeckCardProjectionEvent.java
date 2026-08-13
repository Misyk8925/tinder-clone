package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Full, versioned profile card projection published to
 * {@code profile.deck-card-projection.v1}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileDeckCardProjectionEvent(
        @NotNull UUID eventId,
        @NotNull UUID profileId,
        @NotNull String userId,
        @Min(1) long version,
        @NotNull Instant occurredAt,
        @NotNull ProfileProjectionOperation operation,
        @NotNull ProjectionSource source,
        UUID backfillRunId,
        @NotNull @Valid DeckCardProjection card
) {
    public ProfileDeckCardProjectionEvent {
        if (source == ProjectionSource.BACKFILL && backfillRunId == null) {
            throw new IllegalArgumentException("backfillRunId is required for BACKFILL events");
        }
        if (source == ProjectionSource.LIVE && backfillRunId != null) {
            throw new IllegalArgumentException("backfillRunId must be absent for LIVE events");
        }
        if (!profileId.equals(card.profileId())) {
            throw new IllegalArgumentException("event profileId must equal card profileId");
        }
    }
}
