package com.tinder.clone.consumer.kafka;

import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.service.SwipeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SwipesConsumer}.
 * Verifies correct delegation to {@link SwipeService} and Kafka acknowledgement behaviour.
 */
@ExtendWith(MockitoExtension.class)
class SwipesConsumerTest {

    @Mock
    private SwipeService swipeService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private SwipesConsumer swipesConsumer;

    private SwipeCreatedEvent buildEvent(boolean decision) {
        return SwipeCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .profile1Id(UUID.randomUUID().toString())
                .profile2Id(UUID.randomUUID().toString())
                .decision(decision)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    void handleSwipeCreatedEvent_delegatesToSwipeService_andAcknowledges() {
        SwipeCreatedEvent event = buildEvent(true);

        swipesConsumer.handleSwipeCreatedEvent(event, 0, 0L, acknowledgment);

        verify(swipeService).save(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleSwipeCreatedEvent_acknowledgesMessage_forDislikeEvent() {
        SwipeCreatedEvent event = buildEvent(false);

        swipesConsumer.handleSwipeCreatedEvent(event, 1, 42L, acknowledgment);

        verify(swipeService).save(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleSwipeCreatedEvent_doesNotAcknowledge_whenSwipeServiceThrows() {
        SwipeCreatedEvent event = buildEvent(true);
        doThrow(new RuntimeException("DB error")).when(swipeService).save(event);

        // Must not propagate exception — consumer silently logs and skips ack
        swipesConsumer.handleSwipeCreatedEvent(event, 0, 0L, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleSwipeCreatedEvent_passesExactEventToService() {
        SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                .eventId("evt-123")
                .profile1Id("aaaa-profile")
                .profile2Id("bbbb-profile")
                .decision(true)
                .timestamp(1_700_000_000_000L)
                .build();

        swipesConsumer.handleSwipeCreatedEvent(event, 2, 99L, acknowledgment);

        verify(swipeService).save(event);
        verifyNoMoreInteractions(swipeService);
    }
}

