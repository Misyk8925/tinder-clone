package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.kafka.event.SwipeCreatedEvent;
import com.tinder.clone.consumer.kafka.event.SwipeSavedEvent;
import com.tinder.clone.consumer.outbox.model.SwipeEventOutbox;
import com.tinder.clone.consumer.outbox.model.SwipeOutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwipeOutboxService {

    private final SwipeEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueueSwipeSaved(SwipeCreatedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(event.getEventId(), "eventId must not be null");
        Objects.requireNonNull(event.getProfile1Id(), "profile1Id must not be null");
        Objects.requireNonNull(event.getProfile2Id(), "profile2Id must not be null");

        SwipeSavedEvent outboxEvent = SwipeSavedEvent.builder()
                .eventId(event.getEventId())
                .profile1Id(event.getProfile1Id())
                .profile2Id(event.getProfile2Id())
                .decision(event.isDecision())
                .timestamp(event.getTimestamp())
                .build();

        SwipeEventOutbox outboxRow = SwipeEventOutbox.pending(
                UUID.fromString(event.getEventId()),
                UUID.fromString(event.getProfile1Id()),
                UUID.fromString(event.getProfile2Id()),
                SwipeOutboxEventType.SWIPE_SAVED,
                objectMapper.writeValueAsString(outboxEvent),
                Instant.now()
        );

        outboxRepository.save(outboxRow);
    }
}
