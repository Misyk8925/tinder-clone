package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to topic {@code profile.deleted} when a profile is soft-deleted.
 * Consumers (deck builder, match service) must remove the profile from active decks and
 * invalidate any cached references.
 *
 * @param eventId    unique identifier for this event instance; use for deduplication
 * @param profileId  UUID of the deleted profile
 * @param occurredAt epoch-millis UTC when the deletion was persisted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileDeletedEvent(
        @NotNull UUID eventId,
        @NotNull UUID profileId,
        @JsonProperty("timestamp") @NotNull Instant occurredAt
) {}
