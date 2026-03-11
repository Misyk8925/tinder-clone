package com.tinder.clone.consumer.kafka;

import com.tinder.clone.consumer.kafka.event.ProfileCreateEvent;
import com.tinder.clone.consumer.kafka.event.ProfileDeleteEvent;
import com.tinder.clone.consumer.service.ProfileEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProfileEventConsumer}.
 * Verifies correct routing to {@link ProfileEventService} and acknowledgement behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ProfileEventConsumerTest {

    @Mock
    private ProfileEventService profileEventService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private ProfileEventConsumer profileEventConsumer;

    // --- handleProfileCreatedEvent ---

    @Test
    void handleProfileCreatedEvent_delegatesToService_andAcknowledges() {
        ProfileCreateEvent event = ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();

        profileEventConsumer.handleProfileCreatedEvent(event, 0, 0L, acknowledgment);

        verify(profileEventService).saveProfileCache(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleProfileCreatedEvent_doesNotAcknowledge_whenServiceThrows() {
        ProfileCreateEvent event = ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();
        doThrow(new RuntimeException("Redis failure")).when(profileEventService).saveProfileCache(event);

        profileEventConsumer.handleProfileCreatedEvent(event, 0, 0L, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleProfileCreatedEvent_handlesNullTimestamp_gracefully() {
        ProfileCreateEvent event = ProfileCreateEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .timestamp(null)
                .build();

        profileEventConsumer.handleProfileCreatedEvent(event, 0, 1L, acknowledgment);

        verify(profileEventService).saveProfileCache(event);
        verify(acknowledgment).acknowledge();
    }

    // --- handleProfileDeletedEvent ---

    @Test
    void handleProfileDeletedEvent_delegatesToService_andAcknowledges() {
        ProfileDeleteEvent event = ProfileDeleteEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();

        profileEventConsumer.handleProfileDeletedEvent(event, 0, 0L, acknowledgment);

        verify(profileEventService).deleteProfileCache(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleProfileDeletedEvent_acknowledgesEvenWhenServiceThrows() {
        // The delete handler catches exceptions and still acknowledges
        ProfileDeleteEvent event = ProfileDeleteEvent.builder()
                .eventId(UUID.randomUUID())
                .profileId(UUID.randomUUID())
                .timestamp(Instant.now())
                .build();
        doThrow(new RuntimeException("DB error")).when(profileEventService).deleteProfileCache(event);

        profileEventConsumer.handleProfileDeletedEvent(event, 0, 0L, acknowledgment);

        verify(acknowledgment).acknowledge();
    }
}

