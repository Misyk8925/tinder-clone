package com.tinder.profiles.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import com.tinder.profiles.outbox.model.ProfileOutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileOutboxService {

    private final ProfileEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueueProfileCreated(ProfileCreateEvent event) {
        enqueue(event.getEventId(), event.getProfileId(), ProfileOutboxEventType.PROFILE_CREATED, event);
    }

    public void enqueueProfileUpdated(ProfileUpdatedEvent event) {
        enqueue(event.getEventId(), event.getProfileId(), ProfileOutboxEventType.PROFILE_UPDATED, event);
    }

    public void enqueueProfileDeleted(ProfileDeleteEvent event) {
        enqueue(event.getEventId(), event.getProfileId(), ProfileOutboxEventType.PROFILE_DELETED, event);
    }

    private void enqueue(UUID eventId, UUID profileId, ProfileOutboxEventType eventType, Object eventPayload) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(profileId, "profileId must not be null");
        Objects.requireNonNull(eventPayload, "eventPayload must not be null");

        ProfileEventOutbox outboxRow = ProfileEventOutbox.pending(
                eventId,
                profileId,
                eventType,
                serialize(eventPayload),
                Instant.now()
        );

        outboxRepository.save(outboxRow);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize outbox payload", ex);
        }
    }
}
