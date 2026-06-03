package com.tinder.profiles.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.profiles.kafka.ResilientProfileEventProducer;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import com.tinder.profiles.outbox.model.ProfileOutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileOutboxEventDispatcherTest {

    @Mock
    private ResilientProfileEventProducer resilientProfileEventProducer;

    private ObjectMapper objectMapper;
    private ProfileOutboxEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        dispatcher = new ProfileOutboxEventDispatcher(objectMapper, resilientProfileEventProducer);
        ReflectionTestUtils.setField(dispatcher, "profileUpdatedEventsTopic", "profile.updated");
        ReflectionTestUtils.setField(dispatcher, "profileCreatedEventsTopic", "profile.created");
        ReflectionTestUtils.setField(dispatcher, "profileDeletedEventsTopic", "profile.deleted");
    }

    @Test
    void publish_ShouldRouteCreateEventByType() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileCreatedEvent event = new ProfileCreatedEvent(eventId, profileId, "user-123", Instant.now());

        ProfileEventOutbox outboxRow = ProfileEventOutbox.pending(
                eventId,
                profileId,
                ProfileOutboxEventType.PROFILE_CREATED,
                objectMapper.writeValueAsString(event),
                Instant.now()
        );

        dispatcher.publish(outboxRow);

        verify(resilientProfileEventProducer).sendProfileCreateEvent(
                argThat(actual -> actual.eventId().equals(eventId) && actual.profileId().equals(profileId)),
                eq(profileId.toString()),
                eq("profile.created")
        );
    }

    @Test
    void publish_ShouldRouteDeleteEventByType() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileDeletedEvent event = new ProfileDeletedEvent(eventId, profileId, Instant.now());

        ProfileEventOutbox outboxRow = ProfileEventOutbox.pending(
                eventId,
                profileId,
                ProfileOutboxEventType.PROFILE_DELETED,
                objectMapper.writeValueAsString(event),
                Instant.now()
        );

        dispatcher.publish(outboxRow);

        verify(resilientProfileEventProducer).sendProfileDeleteEvent(
                argThat(actual -> actual.eventId().equals(eventId) && actual.profileId().equals(profileId)),
                eq(profileId.toString()),
                eq("profile.deleted")
        );
    }

    @Test
    void publish_ShouldFailOnInvalidPayload() {
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileEventOutbox outboxRow = ProfileEventOutbox.pending(
                eventId,
                profileId,
                ProfileOutboxEventType.PROFILE_UPDATED,
                "not-json",
                Instant.now()
        );

        assertThatThrownBy(() -> dispatcher.publish(outboxRow))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to deserialize outbox payload");
    }
}
