package com.tinder.profiles.kafka.dto;

import java.time.Instant;

public class MatchCreateEvent {

    private String eventId;
    private String profile1Id;
    private String profile2Id;
    private Instant createdAt;

    public MatchCreateEvent() {
    }

    public MatchCreateEvent(String eventId, String profile1Id, String profile2Id, Instant createdAt) {
        this.eventId = eventId;
        this.profile1Id = profile1Id;
        this.profile2Id = profile2Id;
        this.createdAt = createdAt;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getProfile1Id() {
        return profile1Id;
    }

    public void setProfile1Id(String profile1Id) {
        this.profile1Id = profile1Id;
    }

    public String getProfile2Id() {
        return profile2Id;
    }

    public void setProfile2Id(String profile2Id) {
        this.profile2Id = profile2Id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
