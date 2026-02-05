package com.tinder.deck.kafka.consumer;

import com.tinder.deck.kafka.dto.SwipeCreatedEvent;
import com.tinder.deck.service.DeckCache;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SwipeEventConsumerTest {

    @Mock
    private DeckCache deckCache;

    private SwipeEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SwipeEventConsumer(deckCache);
    }

    @Test
    @DisplayName("Removes swiped profile from deck")
    void removesFromDeck() {
        UUID profile1Id = UUID.randomUUID();
        UUID profile2Id = UUID.randomUUID();

        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(profile1Id.toString())
                .profile2Id(profile2Id.toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(deckCache.removeFromDeck(profile1Id, profile2Id)).thenReturn(Mono.just(1L));

        consumer.consume(event, 0, 1L);

        verify(deckCache).removeFromDeck(profile1Id, profile2Id);
    }

    @Test
    @DisplayName("Invalid UUIDs propagate (to allow retry/DLT)")
    void invalidUuidPropagates() {
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id("invalid-uuid")
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        assertThrows(IllegalArgumentException.class, () -> consumer.consume(event, 0, 1L));
    }

    @Test
    @DisplayName("DeckCache failures propagate (to allow retry/DLT)")
    void deckCacheFailurePropagates() {
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(true)
                .timestamp(System.currentTimeMillis())
                .build();

        when(deckCache.removeFromDeck(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.error(new RuntimeException("Redis down")));

        assertThrows(RuntimeException.class, () -> consumer.consume(event, 0, 1L));
    }
}

