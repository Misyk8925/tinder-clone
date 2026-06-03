package com.tinder.deck.kafka.consumer;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.DeckService;
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

    @Mock
    private ProfilesHttp profilesHttp;

    @Mock
    private DeckService deckService;

    private ProfileEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProfileEventConsumer(deckCache, profilesHttp, deckService);
    }

    @Test
    @DisplayName("PREFERENCES change invalidates only personal deck")
    void preferencesChangeInvalidatesPersonalDeck() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.PREFERENCES,
                Set.of("preferences.minAge", "preferences.maxAge"), Instant.now(), null);

        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));

        consumer.consumeProfileUpdate(event, 0, 1L);

        verify(deckCache).invalidate(profileId);
        verify(deckCache, never()).markProfileInvalidated(any());
    }

    @Test
    @DisplayName("CRITICAL_FIELDS change marks profile invalidated globally")
    void criticalFieldsChangeMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.CRITICAL_FIELDS,
                Set.of("age", "gender"), Instant.now(), null);

        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));
        when(deckCache.removeFromAllDecks(profileId)).thenReturn(Mono.just(0L));

        consumer.consumeProfileUpdate(event, 0, 2L);

        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache).removeFromAllDecks(profileId);
        verify(deckCache, never()).invalidate(any(UUID.class));
    }

    @Test
    @DisplayName("LOCATION_CHANGE invalidates personal deck and marks profile invalidated globally")
    void locationChangeInvalidatesAndMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.LOCATION_CHANGE,
                Set.of("location.city"), Instant.now(), null);

        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));
        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));
        when(deckCache.removeFromAllDecks(profileId)).thenReturn(Mono.just(0L));
        // getProfile returns empty -> proactive rebuild is skipped (deckService not invoked).
        when(profilesHttp.getProfile(profileId)).thenReturn(Mono.empty());

        consumer.consumeProfileUpdate(event, 0, 3L);

        verify(deckCache).invalidate(profileId);
        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache).removeFromAllDecks(profileId);
    }

    @Test
    @DisplayName("DeckCache failures propagate (to allow retry/DLT)")
    void failuresPropagate() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.PREFERENCES,
                Set.of("preferences"), Instant.now(), null);

        when(deckCache.invalidate(profileId)).thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThrows(RuntimeException.class, () -> consumer.consumeProfileUpdate(event, 0, 4L));
    }
}
