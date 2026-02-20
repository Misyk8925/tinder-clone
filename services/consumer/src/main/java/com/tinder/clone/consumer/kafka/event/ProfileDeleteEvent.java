package com.tinder.clone.consumer.kafka.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ProfileDeleteEvent {

    UUID eventId;
    UUID profileId;
    Instant timestamp;

    @JsonCreator
    public ProfileDeleteEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("profileId") UUID profileId,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        this.eventId = eventId;
        this.profileId = profileId;
        this.timestamp = timestamp;
    }
}
