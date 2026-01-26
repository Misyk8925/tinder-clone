package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.SwipeCreatedEvent;
import com.tinder.deck.service.DeckCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SwipeEventConsumer
 */
@ExtendWith(MockitoExtension.class)
class SwipeEventConsumerTest {

    @Mock
    private DeckCache deckCache;

    private SwipeEventConsumer consumer;
    private Acknowledgment acknowledgment;

    @BeforeEach
    void setUp() {
        consumer = new SwipeEventConsumer(deckCache);
        acknowledgment = mock(Acknowledgment.class);


    }

    @Test
    @DisplayName("Should acknowledge right swipe event")
    void testConsumeRightSwipeEvent() {
        // Given
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(true) // right swipe
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 1L, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge left swipe event")
    void testConsumeLeftSwipeEvent() {
        // Given
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(false) // left swipe
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 2L, acknowledgment);

        // Then
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge event even if exception occurs")
    void testConsumeWithException() {
        // Given: Create event that might cause issues
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(null) // Potential NPE trigger
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 3L, acknowledgment);

        // Then: Should still acknowledge to prevent infinite retry
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should handle multiple swipes from same user")
    void testConsumeMultipleSwipes() {
        // Given
        String profile1Id = UUID.randomUUID().toString();

        SwipeCreatedEvent event1 = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1Id)
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        SwipeCreatedEvent event2 = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1Id)
                .profile2Id(UUID.randomUUID().toString())
                .decision(false)
                .timestamp(System.currentTimeMillis() + 1000)
                .build();

        // When
        consumer.consume(event1, 0, 4L, acknowledgment);
        consumer.consume(event2, 0, 5L, acknowledgment);

        // Then
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    @DisplayName("Should call DeckCache to remove profile after swipe")
    void testShouldRemoveProfileFromDeck() {
        // Given
        UUID profile1Id = UUID.randomUUID();
        UUID profile2Id = UUID.randomUUID();

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1Id.toString())
                .profile2Id(profile2Id.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 1L, acknowledgment);

        // Then
        verify(deckCache, times(1)).removeFromDeck(profile1Id, profile2Id);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should handle DeckCache errors gracefully")
    void testShouldHandleDeckCacheErrors() {
        // Given: DeckCache throws error
        when(deckCache.removeFromDeck(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis error")));

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 1L, acknowledgment);

        // Then: Should still acknowledge to prevent infinite retry
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should handle invalid UUID format in event")
    void testShouldHandleInvalidUUID() {
        // Given: Event with invalid UUID
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id("invalid-uuid")
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        // When
        consumer.consume(event, 0, 1L, acknowledgment);

        // Then: Should acknowledge without calling DeckCache
        verify(deckCache, never()).removeFromDeck(any(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }
}
