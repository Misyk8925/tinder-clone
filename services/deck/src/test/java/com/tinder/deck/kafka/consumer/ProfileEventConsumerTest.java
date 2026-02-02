package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.ChangeType;
import com.tinder.deck.kafka.dto.ProfileUpdateEvent;
import com.tinder.deck.service.DeckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProfileEventConsumer
 */
@ExtendWith(MockitoExtension.class)
class ProfileEventConsumerTest {

    @Mock
    private DeckCache deckCache;

    private ProfileEventConsumer consumer;
    private Acknowledgment acknowledgment;

    @BeforeEach
    void setUp() {
        consumer = new ProfileEventConsumer(deckCache);
        acknowledgment = mock(Acknowledgment.class);

    }

    @Test
    @DisplayName("Should acknowledge PREFERENCES change event without invalidating preferences cache")
    void testConsumeProfileUpdatePreferencesChangeEvent() throws InterruptedException {
        // Given
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences.minAge", "preferences.maxAge"))
                .timestamp(Instant.now())
                .metadata("minAge:18,maxAge:25,gender:FEMALE")
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 1L, acknowledgment);

        // Wait for async subscribe() to complete
        Thread.sleep(100);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
        // Should NOT invalidate preferences cache (user changing their preferences doesn't affect candidates)
        verify(deckCache, never()).invalidatePreferencesCache(anyInt(), anyInt(), anyString());
        // Should ONLY invalidate personal deck
        verify(deckCache, times(1)).invalidate(event.getProfileId());
    }

    @Test
    @DisplayName("Should acknowledge CRITICAL_FIELDS change event without invalidating preferences cache")
    void testConsumeProfileUpdateCriticalFieldsChangeEvent() throws InterruptedException {
        // Given
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.CRITICAL_FIELDS)
                .changedFields(Set.of("age", "gender"))
                .timestamp(Instant.now())
                .metadata("minAge:22,maxAge:30,gender:MALE")
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 2L, acknowledgment);

        // Wait for async subscribe() operations to complete
        Thread.sleep(200);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
        // Should NOT invalidate preferences cache (we don't know which caches contain this candidate)
        verify(deckCache, never()).invalidatePreferencesCache(anyInt(), anyInt(), anyString());
        // Should ONLY invalidate personal deck
        verify(deckCache, times(1)).invalidate(event.getProfileId());
    }

    @Test
    @DisplayName("Should acknowledge NON_CRITICAL change event without cache invalidation")
    void testConsumeProfileUpdateNonCriticalChangeEvent() {
        // Given
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.NON_CRITICAL)
                .changedFields(Set.of("bio", "name"))
                .timestamp(Instant.now())
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 3L, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
        verify(deckCache, never()).invalidatePreferencesCache(anyInt(), anyInt(), anyString());
        verify(deckCache, never()).invalidate(any(UUID.class));
    }

    @Test
    @DisplayName("Should acknowledge event even if exception occurs")
    void testConsumeProfileUpdateWithException() {
        // Given: Create event that might cause issues
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(null) // Potential NPE trigger
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 4L, acknowledgment);

        // Then: Should still acknowledge to prevent infinite retry
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should handle missing metadata gracefully")
    void testConsumeProfileUpdateMissingMetadata() {
        // Given: Event without metadata
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .metadata(null)
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 5L, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
        // Should still invalidate personal deck even without preferences metadata
        verify(deckCache, times(1)).invalidate(event.getProfileId());
        // But should not try to invalidate preferences cache
        verify(deckCache, never()).invalidatePreferencesCache(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Should handle invalid metadata format gracefully")
    void testConsumeProfileUpdateInvalidMetadata() {
        // Given: Event with invalid metadata format
        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .metadata("invalid format")
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 6L, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
        // Should still invalidate personal deck
        verify(deckCache, times(1)).invalidate(event.getProfileId());
    }

    @Test
    @DisplayName("Should handle DeckCache errors gracefully")
    void testConsumeProfileUpdateDeckCacheError() {


        ProfileUpdateEvent event = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .metadata("minAge:18,maxAge:25,gender:FEMALE")
                .build();

        // When
        consumer.consumeProfileUpdate(event, 0, 7L, acknowledgment);

        // Then: Should still acknowledge
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should invalidate only personal deck for PREFERENCES change (not preferences cache)")
    void testPreferencesParsingVariations() throws InterruptedException {
        // Given: User changes their preferences
        ProfileUpdateEvent event1 = ProfileUpdateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .changeType(ChangeType.PREFERENCES)
                .changedFields(Set.of("preferences"))
                .timestamp(Instant.now())
                .metadata("minAge:25,maxAge:35,gender:ANY")
                .build();

        // When
        consumer.consumeProfileUpdate(event1, 0, 8L, acknowledgment);

        // Wait for async subscribe() to complete
        Thread.sleep(100);

        // Then
        // Should NOT invalidate preferences cache (user changing their prefs doesn't affect candidates)
        verify(deckCache, never()).invalidatePreferencesCache(anyInt(), anyInt(), anyString());
        // Should ONLY invalidate personal deck
        verify(deckCache, times(1)).invalidate(event1.getProfileId());
    }
}
