package com.tinder.deckread.messaging;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("acceptance")
@DisplayName("Feature: failed Deck Read events use bounded processing and a sanitized DLT")
class DeckReadKafkaConfigurationAcceptanceTest {

    private static final String SERIALIZER = SanitizedDeckReadDltSerializer.class.getName();

    @Test
    @DisplayName("Scenario: Given a materializer exhausts its retries, when the connector writes its DLT record, then every input uses the sanitized serializer")
    void everyInputUsesTheSanitizedDeadLetterSerializer() {
        // Given
        Config config = ConfigProvider.getConfig();

        // When / Then
        assertThat(config.getValue(
                "mp.messaging.incoming.profile-deck-card-projection.dead-letter-queue.value.serializer",
                String.class)).isEqualTo(SERIALIZER);
        assertThat(config.getValue(
                "mp.messaging.incoming.swipe-saved.dead-letter-queue.value.serializer",
                String.class)).isEqualTo(SERIALIZER);
        assertThat(config.getValue(
                "mp.messaging.incoming.match-created.dead-letter-queue.value.serializer",
                String.class)).isEqualTo(SERIALIZER);
    }
}
