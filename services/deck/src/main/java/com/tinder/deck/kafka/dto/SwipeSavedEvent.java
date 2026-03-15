package com.tinder.deck.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Event published by consumer service after a swipe is persisted.
 * Consumed by Deck Service to update deck cache.
 */
@Value
@Builder
public class SwipeSavedEvent {

    String eventId;
    String profile1Id;
    String profile2Id;
    boolean decision;
    long timestamp;

    @JsonCreator
    public SwipeSavedEvent(
            @JsonProperty("eventId") String eventId,
            @JsonProperty("profile1Id") String profile1Id,
            @JsonProperty("profile2Id") String profile2Id,
            @JsonProperty("decision") boolean decision,
            @JsonProperty("timestamp") long timestamp
    ) {
        this.eventId = eventId;
        this.profile1Id = profile1Id;
        this.profile2Id = profile2Id;
        this.decision = decision;
        this.timestamp = timestamp;
    }
}
