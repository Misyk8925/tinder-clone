package com.tinder.profiles.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tinder.profiles.kafka.dto.ChangeType;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileUpdatedEvent;
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
        ProfileCreateEvent event = ProfileCreateEvent.builder()
                .eventId(eventId)
                .profileId(profileId)
                .timestamp(Instant.parse("2026-02-15T09:00:00Z"))
                .build();

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
        ProfileUpdatedEvent event = ProfileUpdatedEvent.builder()
                .eventId(eventId)
                .profileId(profileId)
                .changeType(ChangeType.CRITICAL_FIELDS)
                .changedFields(Set.of("age", "gender"))
                .timestamp(Instant.parse("2026-02-15T09:10:00Z"))
                .metadata("Profile updated: CRITICAL_FIELDS")
                .build();

        outboxService.enqueueProfileUpdated(event);

        ArgumentCaptor<ProfileEventOutbox> captor = ArgumentCaptor.forClass(ProfileEventOutbox.class);
        verify(outboxRepository).save(captor.capture());

        ProfileEventOutbox row = captor.getValue();
        assertThat(row.getEventType()).isEqualTo(ProfileOutboxEventType.PROFILE_UPDATED);
        assertThat(row.getPayload()).contains("CRITICAL_FIELDS");
        assertThat(row.getPayload()).contains("\"changedFields\"");
    }
}
