package com.tinder.profiles.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.infrastructure.messaging.kafka.ResilientProfileEventProducer;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.profiles.config.props.KafkaTopicProperties;
import com.tinder.profiles.infrastructure.messaging.outbox.model.ProfileEventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProfileOutboxEventDispatcher {

    private final ObjectMapper objectMapper;
    private final ResilientProfileEventProducer resilientProfileEventProducer;
    private final KafkaTopicProperties topics;

    public void publish(ProfileEventOutbox outboxRow) {
        String key = outboxRow.getProfileId().toString();

        switch (outboxRow.getEventType()) {
            case PROFILE_CREATED -> resilientProfileEventProducer.sendProfileCreateEvent(
                    deserialize(outboxRow, ProfileCreatedEvent.class),
                    key,
                    topics.created()
            );
            case PROFILE_UPDATED -> resilientProfileEventProducer.sendProfileUpdateEvent(
                    deserialize(outboxRow, ProfileUpdatedEvent.class),
                    key,
                    topics.updated()
            );
            case PROFILE_DELETED -> resilientProfileEventProducer.sendProfileDeleteEvent(
                    deserialize(outboxRow, ProfileDeletedEvent.class),
                    key,
                    topics.deleted()
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
