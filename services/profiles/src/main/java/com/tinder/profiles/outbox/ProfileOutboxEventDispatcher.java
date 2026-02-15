package com.tinder.profiles.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.kafka.ResilientProfileEventProducer;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileDeleteEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileOutboxEventDispatcher {

    private final ObjectMapper objectMapper;
    private final ResilientProfileEventProducer resilientProfileEventProducer;

    @Value("${kafka.topics.profile-events.updated}")
    private String profileUpdatedEventsTopic;

    @Value("${kafka.topics.profile-events.created}")
    private String profileCreatedEventsTopic;

    @Value("${kafka.topics.profile-events.deleted}")
    private String profileDeletedEventsTopic;

    public void publish(ProfileEventOutbox outboxRow) {
        String key = outboxRow.getProfileId().toString();

        switch (outboxRow.getEventType()) {
            case PROFILE_CREATED -> resilientProfileEventProducer.sendProfileCreateEvent(
                    deserialize(outboxRow, ProfileCreateEvent.class),
                    key,
                    profileCreatedEventsTopic
            );
            case PROFILE_UPDATED -> resilientProfileEventProducer.sendProfileUpdateEvent(
                    deserialize(outboxRow, ProfileUpdatedEvent.class),
                    key,
                    profileUpdatedEventsTopic
            );
            case PROFILE_DELETED -> resilientProfileEventProducer.sendProfileDeleteEvent(
                    deserialize(outboxRow, ProfileDeleteEvent.class),
                    key,
                    profileDeletedEventsTopic
            );
        }
    }

    private <T> T deserialize(ProfileEventOutbox outboxRow, Class<T> clazz) {
        try {
            return objectMapper.readValue(outboxRow.getPayload(), clazz);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Unable to deserialize outbox payload for row " + outboxRow.getId(), ex
            );
        }
    }
}
