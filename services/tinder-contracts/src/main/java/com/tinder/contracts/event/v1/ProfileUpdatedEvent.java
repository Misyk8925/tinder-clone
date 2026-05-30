package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Published to topic {@code profile.updated} when a profile field changes.
 * Consumers should inspect {@link #changeType} to determine urgency.
 *
 * @param eventId       unique identifier for this event instance; use for deduplication
 * @param profileId     UUID of the updated profile
 * @param changeType    classification of the change; drives consumer reaction (see {@link ChangeType})
 * @param changedFields set of field names that changed (e.g. {@code "bio"}, {@code "age"})
 * @param occurredAt    epoch-millis UTC when the update was persisted
 * @param metadata      optional JSON string with before/after values for audit; may be null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdatedEvent(
        @NotNull UUID eventId,
        @NotNull UUID profileId,
        @NotNull ChangeType changeType,
        @NotNull Set<String> changedFields,
        @JsonProperty("timestamp") @NotNull Instant occurredAt,
        String metadata
) {}
