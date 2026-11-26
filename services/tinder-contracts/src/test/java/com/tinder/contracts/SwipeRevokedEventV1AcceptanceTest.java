package com.tinder.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.SwipeRevokedEventV1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Shared event contract acceptance for FR-6 and NFR-6. */
@Tag("acceptance")
@DisplayName("Feature: SwipeRevokedEventV1 wire contract is stable and forward-compatible (FR-6)")
class SwipeRevokedEventV1AcceptanceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Scenario: Given a SwipeRevokedEventV1, when serialized to JSON and parsed back, then all compensating fields match and topic is swipe-revoked")
    void swipeRevokedEventRoundTripsCleanly() throws Exception {
        // Given
        var event = new SwipeRevokedEventV1(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                false,
                1_795_643_650_000L,
                1_795_643_712_410L
        );

        // When
        String json = mapper.writeValueAsString(event);
        SwipeRevokedEventV1 parsed = mapper.readValue(json, SwipeRevokedEventV1.class);

        // Then
        assertEquals("swipe-revoked", SwipeRevokedEventV1.TOPIC);
        assertEquals(event, parsed);
        assertFalse(parsed.originalDecision());
    }

    @Test
    @DisplayName("Scenario: Given a producer adds a future optional field, when SwipeRevokedEventV1 is deserialized, then unknown fields are ignored")
    void swipeRevokedEventIgnoresUnknownFutureFields() throws Exception {
        // Given
        String eventId = UUID.randomUUID().toString();
        String revokedSwipeEventId = UUID.randomUUID().toString();
        String profile1Id = UUID.randomUUID().toString();
        String profile2Id = UUID.randomUUID().toString();
        String json = """
                {
                  "eventId": "%s",
                  "revokedSwipeEventId": "%s",
                  "profile1Id": "%s",
                  "profile2Id": "%s",
                  "originalDecision": true,
                  "swipedAt": 1795643650000,
                  "revokedAt": 1795643712410,
                  "futureField": "ignored"
                }
                """.formatted(eventId, revokedSwipeEventId, profile1Id, profile2Id);

        // When
        SwipeRevokedEventV1 parsed = mapper.readValue(json, SwipeRevokedEventV1.class);

        // Then
        assertEquals(eventId, parsed.eventId());
        assertEquals(revokedSwipeEventId, parsed.revokedSwipeEventId());
        assertEquals(profile1Id, parsed.profile1Id());
        assertEquals(profile2Id, parsed.profile2Id());
    }
}
