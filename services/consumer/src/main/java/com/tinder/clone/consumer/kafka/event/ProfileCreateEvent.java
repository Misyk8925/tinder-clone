package com.tinder.clone.consumer.kafka.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class ProfileCreateEvent {

    UUID eventId;
    UUID profileId;
    String userId;
    Instant timestamp;

    @JsonCreator
    public ProfileCreateEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("profileId") UUID profileId,
            @JsonProperty("userId") String userId,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        this.eventId = eventId;
        this.profileId = profileId;
        this.userId = userId;
        this.timestamp = timestamp;
    }
}
