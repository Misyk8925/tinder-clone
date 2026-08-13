package com.tinder.deckread.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.DeckCardPhoto;
import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("acceptance")
@DisplayName("Feature: Deck Read dead-letter payloads are sanitized")
class SanitizedDeckReadDltSerializerTest {

    @Test
    @DisplayName("Scenario: Given a profile card containing PII, when it enters DLT, then only replay metadata is serialized")
    void profileCardPiiIsNotSerialized() throws Exception {
        // Given
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileDeckCardProjectionEvent event = new ProfileDeckCardProjectionEvent(
                eventId, profileId, "sensitive-user-id", 9, Instant.now(),
                ProfileProjectionOperation.UPSERT, ProjectionSource.LIVE, null,
                new DeckCardProjection(
                        profileId, "Sensitive Name", 31, "Private City", "Sensitive Bio", true,
                        new DeckCardPreferences(18, 40, "FEMALE", 25),
                        List.of(new DeckCardPhoto(UUID.randomUUID(), "https://private.example/photo.jpg", 0)),
                        List.of()));

        // When
        byte[] payload = new SanitizedDeckReadDltSerializer().serialize("projection.dlt", event);
        JsonNode json = new ObjectMapper().readTree(payload);

        // Then
        assertThat(json.get("payloadType").asText()).isEqualTo("profile.deck-card-projection.v1");
        assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.get("profileId").asText()).isEqualTo(profileId.toString());
        assertThat(json.get("version").asLong()).isEqualTo(9);
        assertThat(new String(payload, java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("Sensitive Name", "Private City", "Sensitive Bio", "private.example", "sensitive-user-id");
    }
}
