package com.tinder.contracts.event.v1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to topic {@code profile.created} when a new profile completes registration.
 *
 * @param eventId    unique identifier for this event instance; use for deduplication
 * @param profileId  UUID of the newly created profile
 * @param userId     Keycloak subject UUID of the owning user
 * @param occurredAt epoch-millis UTC when the profile was persisted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileCreatedEvent(
        @NotNull UUID eventId,
        @NotNull UUID profileId,
        @NotNull String userId,
        @JsonProperty("timestamp") @NotNull Instant occurredAt
) {}
