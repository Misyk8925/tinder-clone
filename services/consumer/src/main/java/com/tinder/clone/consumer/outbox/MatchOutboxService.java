package com.tinder.clone.consumer.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.tinder.clone.consumer.kafka.event.MatchCreateEvent;
import com.tinder.clone.consumer.outbox.model.MatchEventOutbox;
import com.tinder.clone.consumer.outbox.model.MatchOutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchOutboxService {

    private final MatchEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueue(MatchCreateEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(event.getEventId(), "eventId must not be null");
        Objects.requireNonNull(event.getProfile1Id(), "profile1Id must not be null");
        Objects.requireNonNull(event.getProfile2Id(), "profile2Id must not be null");

        MatchEventOutbox outboxRow = MatchEventOutbox.pending(
                UUID.fromString(event.getEventId()),
                UUID.fromString(event.getProfile1Id()),
                UUID.fromString(event.getProfile2Id()),
                MatchOutboxEventType.MATCH_CREATED,
                objectMapper.writeValueAsString(event),
                Instant.now()
        );

        outboxRepository.save(outboxRow);
    }
}
