package com.tinder.deck.kafka.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Event published when a swipe is created
 * Consumed by Deck Service to remove swiped profiles from decks
 */
@Value
@Builder
public class SwipeCreatedEvent {

    String eventId;


    String profile1Id;

    String profile2Id;

    boolean decision;

    long timestamp;

    @JsonCreator
    public SwipeCreatedEvent(
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
