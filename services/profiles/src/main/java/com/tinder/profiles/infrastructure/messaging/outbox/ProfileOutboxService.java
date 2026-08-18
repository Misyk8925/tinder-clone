package com.tinder.profiles.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.profiles.infrastructure.messaging.outbox.model.ProfileEventOutbox;
import com.tinder.profiles.infrastructure.messaging.outbox.model.ProfileOutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProfileOutboxService {

    private final ProfileEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void enqueueProfileCreated(ProfileCreatedEvent event) {
        enqueue(event.eventId(), event.profileId(), ProfileOutboxEventType.PROFILE_CREATED, event);
    }

    public void enqueueProfileUpdated(ProfileUpdatedEvent event) {
        enqueue(event.eventId(), event.profileId(), ProfileOutboxEventType.PROFILE_UPDATED, event);
    }

    public void enqueueProfileDeleted(ProfileDeletedEvent event) {
        enqueue(event.eventId(), event.profileId(), ProfileOutboxEventType.PROFILE_DELETED, event);
    }

    public void enqueueDeckCardProjection(ProfileDeckCardProjectionEvent event) {
        if (event.source() != ProjectionSource.LIVE || event.backfillRunId() != null) {
            throw new IllegalArgumentException("BACKFILL projection must retain its run linkage");
        }
        enqueue(event.eventId(), event.profileId(), ProfileOutboxEventType.DECK_CARD_PROJECTION, event, null);
    }

    public void enqueueDeckCardProjection(ProfileDeckCardProjectionEvent event, UUID backfillRunId) {
        Objects.requireNonNull(backfillRunId, "backfillRunId must not be null");
        if (event.source() != ProjectionSource.BACKFILL
                || !backfillRunId.equals(event.backfillRunId())) {
            throw new IllegalArgumentException("BACKFILL event and outbox run linkage must match");
        }
        enqueue(event.eventId(), event.profileId(), ProfileOutboxEventType.DECK_CARD_PROJECTION, event, backfillRunId);
    }

    private void enqueue(UUID eventId, UUID profileId, ProfileOutboxEventType eventType, Object eventPayload) {
        enqueue(eventId, profileId, eventType, eventPayload, null);
    }

    private void enqueue(UUID eventId, UUID profileId, ProfileOutboxEventType eventType,
                         Object eventPayload, UUID backfillRunId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(profileId, "profileId must not be null");
        Objects.requireNonNull(eventPayload, "eventPayload must not be null");

        ProfileEventOutbox outboxRow = ProfileEventOutbox.pending(
                eventId,
                profileId,
                eventType,
                serialize(eventPayload),
                Instant.now(),
                backfillRunId
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
