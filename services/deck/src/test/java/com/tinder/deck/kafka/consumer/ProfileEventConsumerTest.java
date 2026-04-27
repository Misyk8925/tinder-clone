package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.ChangeType;
import com.tinder.deck.kafka.dto.ProfileUpdateEvent;
import com.tinder.deck.service.DeckCache;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileEventConsumerTest {

    @Mock
    private DeckCache deckCache;

    private ProfileEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProfileEventConsumer(deckCache);
    }

    @Test
    @DisplayName("PREFERENCES change invalidates only personal deck")
    void preferencesChangeInvalidatesPersonalDeck() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences.minAge", "preferences.maxAge"))
                .timestamp(Instant.now())
                .build();

        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));

        consumer.consumeProfileUpdate(event, 0, 1L);

        verify(deckCache).invalidate(profileId);
        verify(deckCache, never()).markProfileInvalidated(any());
    }

    @Test
    @DisplayName("CRITICAL_FIELDS change marks profile invalidated globally")
    void criticalFieldsChangeMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .changeType(ChangeType.CRITICAL_FIELDS)
                .changedFields(Set.of("age", "gender"))
                .timestamp(Instant.now())
                .build();

        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));

        consumer.consumeProfileUpdate(event, 0, 2L);

        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache, never()).invalidate(any(UUID.class));
    }

    @Test
    @DisplayName("LOCATION_CHANGE invalidates personal deck and marks profile invalidated globally")
    void locationChangeInvalidatesAndMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .changeType(ChangeType.LOCATION_CHANGE)
                .changedFields(Set.of("location.city"))
                .timestamp(Instant.now())
                .build();

        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));
        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));

        consumer.consumeProfileUpdate(event, 0, 3L);

        verify(deckCache).invalidate(profileId);
        verify(deckCache).markProfileInvalidated(profileId);
    }

    @Test
    @DisplayName("DeckCache failures propagate (to allow retry/DLT)")
    void failuresPropagate() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(profileId)
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .build();

        when(deckCache.invalidate(profileId)).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThrows(RuntimeException.class, () -> consumer.consumeProfileUpdate(event, 0, 4L));
    }
}
