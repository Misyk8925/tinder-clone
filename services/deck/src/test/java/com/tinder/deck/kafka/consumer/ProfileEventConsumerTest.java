package com.tinder.deck.kafka.consumer;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
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
    @DisplayName("CRITICAL_FIELDS change invalidates preferences cache, purges affected decks, and clears their build timestamps")
    void criticalFieldsChangeMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.CRITICAL_FIELDS,
                Set.of("age", "gender"), Instant.now(), null);

        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));
        when(deckCache.invalidateAllPreferencesCaches()).thenReturn(Mono.just(2L));
        when(deckCache.removeFromAllDecks(profileId, true)).thenReturn(Mono.just(0L));

        consumer.consumeProfileUpdate(event, 0, 2L);

        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache).invalidateAllPreferencesCaches();
        verify(deckCache).removeFromAllDecks(profileId, true);
        verify(deckCache, never()).invalidate(any(UUID.class));
    }

    @Test
    @DisplayName("LOCATION_CHANGE invalidates personal deck, marks profile invalidated, and clears affected decks' build timestamps")
    void locationChangeInvalidatesAndMarksInvalidated() {
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.LOCATION_CHANGE,
                Set.of("location.city"), Instant.now(), null);

        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));
        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));
        when(deckCache.removeFromAllDecks(profileId, true)).thenReturn(Mono.just(0L));
        // getProfile returns empty -> proactive rebuild is skipped (deckService not invoked).
        when(profilesHttp.getProfile(profileId)).thenReturn(Mono.empty());

        consumer.consumeProfileUpdate(event, 0, 3L);

        verify(deckCache).invalidate(profileId);
        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache).removeFromAllDecks(profileId, true);
    }

    @Test
    @DisplayName("Profile creation invalidates shared preferences caches")
    void profileCreationInvalidatesPreferencesCaches() {
        UUID profileId = UUID.randomUUID();
        ProfileCreatedEvent event = new ProfileCreatedEvent(
                UUID.randomUUID(), profileId, "user-123", Instant.now());

        when(deckCache.invalidateAllPreferencesCaches()).thenReturn(Mono.just(1L));

        consumer.consumeProfileCreation(event, 0, 5L);

        verify(deckCache).invalidateAllPreferencesCaches();
    }

    @Test
    @DisplayName("Profile deletion purges profile and invalidates shared preferences caches")
    void profileDeletionInvalidatesPreferencesCaches() {
        UUID profileId = UUID.randomUUID();
        ProfileDeletedEvent event = new ProfileDeletedEvent(
                UUID.randomUUID(), profileId, Instant.now());

        when(deckCache.markProfileDeleted(profileId)).thenReturn(Mono.just(true));
        when(deckCache.markProfileInvalidated(profileId)).thenReturn(Mono.just(true));
        when(deckCache.invalidateAllPreferencesCaches()).thenReturn(Mono.just(1L));
        when(deckCache.invalidate(profileId)).thenReturn(Mono.just(1L));
        when(deckCache.removeFromAllDecks(profileId)).thenReturn(Mono.just(1L));

        consumer.consumeProfileDeletion(event, 0, 6L);

        verify(deckCache).markProfileDeleted(profileId);
        verify(deckCache).markProfileInvalidated(profileId);
        verify(deckCache).invalidateAllPreferencesCaches();
        verify(deckCache).invalidate(profileId);
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
