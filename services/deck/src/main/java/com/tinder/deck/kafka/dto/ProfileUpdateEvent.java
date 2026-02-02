package com.tinder.deck.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;


@Value
@Builder
public class ProfileUpdateEvent {

    UUID eventId;

    UUID profileId;

    ChangeType changeType;

    Set<String> changedFields;

    Instant timestamp;

    /**
     * Optional: Previous and new values for critical fields (for debugging/audit)
     */
    String metadata;

    @JsonCreator
    public ProfileUpdateEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("profileId") UUID profileId,
            @JsonProperty("changeType") ChangeType changeType,
            @JsonProperty("changedFields") Set<String> changedFields,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("metadata") String metadata
    ) {
        this.eventId = eventId;
        this.profileId = profileId;
        this.changeType = changeType;
        this.changedFields = changedFields;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }
}
