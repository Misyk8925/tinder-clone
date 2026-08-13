package com.tinder.deckread.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import org.apache.kafka.common.serialization.Serializer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Serializes only replay identifiers and ordering metadata; card/user data never enters a DLT body. */
public final class SanitizedDeckReadDltSerializer implements Serializer<Object> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, Object value) {
        if (value == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (value instanceof ProfileDeckCardProjectionEvent event) {
            sanitized.put("payloadType", "profile.deck-card-projection.v1");
            sanitized.put("eventId", event.eventId());
            sanitized.put("profileId", event.profileId());
            sanitized.put("version", event.version());
            sanitized.put("operation", event.operation());
            sanitized.put("source", event.source());
            sanitized.put("backfillRunId", event.backfillRunId());
        } else if (value instanceof SwipeSavedEvent event) {
            sanitized.put("payloadType", "swipe.saved");
            sanitized.put("eventId", event.eventId());
            sanitized.put("viewerProfileId", event.profile1Id());
            sanitized.put("candidateProfileId", event.profile2Id());
        } else if (value instanceof MatchCreatedEvent event) {
            sanitized.put("payloadType", "match.created");
            sanitized.put("eventId", event.eventId());
            sanitized.put("profile1Id", event.profile1Id());
            sanitized.put("profile2Id", event.profile2Id());
        } else {
            sanitized.put("payloadType", "unknown");
        }
        try {
            return mapper.writeValueAsBytes(sanitized);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize sanitized Deck Read DLT record", e);
        }
    }
}
