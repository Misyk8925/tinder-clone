package com.tinder.profiles.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import com.tinder.profiles.outbox.model.ProfileOutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileOutboxServiceTest {

    @Mock
    private ProfileEventOutboxRepository outboxRepository;

    private ProfileOutboxService outboxService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        outboxService = new ProfileOutboxService(outboxRepository, objectMapper);
        when(outboxRepository.save(any(ProfileEventOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueueProfileCreated_ShouldPersistPendingOutboxRow() {
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileCreatedEvent event = new ProfileCreatedEvent(
                eventId,
                profileId,
                "user-123",
                Instant.parse("2026-02-15T09:00:00Z")
        );

        outboxService.enqueueProfileCreated(event);

        ArgumentCaptor<ProfileEventOutbox> captor = ArgumentCaptor.forClass(ProfileEventOutbox.class);
        verify(outboxRepository).save(captor.capture());

        ProfileEventOutbox row = captor.getValue();
        assertThat(row.getEventId()).isEqualTo(eventId);
        assertThat(row.getProfileId()).isEqualTo(profileId);
        assertThat(row.getEventType()).isEqualTo(ProfileOutboxEventType.PROFILE_CREATED);
        assertThat(row.getRetryCount()).isZero();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getNextAttemptAt()).isNotNull();
        assertThat(row.getPayload()).contains(eventId.toString(), profileId.toString());
    }

    @Test
    void enqueueProfileUpdated_ShouldPersistSerializedPayload() {
        UUID eventId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                eventId,
                profileId,
                ChangeType.CRITICAL_FIELDS,
                Set.of("age", "gender"),
                Instant.parse("2026-02-15T09:10:00Z"),
                "Profile updated: CRITICAL_FIELDS"
        );

        outboxService.enqueueProfileUpdated(event);

        ArgumentCaptor<ProfileEventOutbox> captor = ArgumentCaptor.forClass(ProfileEventOutbox.class);
        verify(outboxRepository).save(captor.capture());

        ProfileEventOutbox row = captor.getValue();
        assertThat(row.getEventType()).isEqualTo(ProfileOutboxEventType.PROFILE_UPDATED);
        assertThat(row.getPayload()).contains("CRITICAL_FIELDS");
        assertThat(row.getPayload()).contains("\"changedFields\"");
    }
}
