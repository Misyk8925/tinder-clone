package com.tinder.profiles.infrastructure.messaging;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.profiles.infrastructure.messaging.outbox.ProfileOutboxService;
import com.tinder.profiles.domain.profile.ProfileChangeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.verify;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventPublisherAdapter")
class OutboxEventPublisherAdapterTest {

    @Mock
    private ProfileOutboxService outboxService;

    @Mock
    private DeckCardProjectionOutboxService deckCardProjectionOutboxService;

    private OutboxEventPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OutboxEventPublisherAdapter(outboxService, deckCardProjectionOutboxService);
    }

    @Test
    @DisplayName("publishCreated enqueues a created event with a generated id and timestamp")
    void publishCreated() {
        UUID profileId = UUID.randomUUID();

        adapter.publishCreated(profileId, "user-1");

        ArgumentCaptor<ProfileCreatedEvent> captor = ArgumentCaptor.forClass(ProfileCreatedEvent.class);
        verify(outboxService).enqueueProfileCreated(captor.capture());
        ProfileCreatedEvent event = captor.getValue();
        then(event.eventId()).isNotNull();
        then(event.profileId()).isEqualTo(profileId);
        then(event.userId()).isEqualTo("user-1");
        then(event.occurredAt()).isNotNull();
        verify(deckCardProjectionOutboxService).enqueueLive(profileId, ProfileProjectionOperation.UPSERT);
    }

    @Test
    @DisplayName("publishUpdated carries the change type and changed fields")
    void publishUpdated() {
        UUID profileId = UUID.randomUUID();
        Set<String> changed = Set.of("age", "gender");

        adapter.publishUpdated(profileId, ProfileChangeType.CRITICAL_FIELDS, changed);

        ArgumentCaptor<ProfileUpdatedEvent> captor = ArgumentCaptor.forClass(ProfileUpdatedEvent.class);
        verify(outboxService).enqueueProfileUpdated(captor.capture());
        ProfileUpdatedEvent event = captor.getValue();
        then(event.profileId()).isEqualTo(profileId);
        then(event.changeType()).isEqualTo(ChangeType.CRITICAL_FIELDS);
        then(event.changedFields()).containsExactlyInAnyOrder("age", "gender");
        then(event.metadata()).contains("CRITICAL_FIELDS");
        verify(deckCardProjectionOutboxService).enqueueLive(profileId, ProfileProjectionOperation.UPSERT);
    }

    @Test
    @DisplayName("publishDeleted enqueues a deleted event for the profile")
    void publishDeleted() {
        UUID profileId = UUID.randomUUID();

        adapter.publishDeleted(profileId);

        ArgumentCaptor<ProfileDeletedEvent> captor = ArgumentCaptor.forClass(ProfileDeletedEvent.class);
        verify(outboxService).enqueueProfileDeleted(captor.capture());
        then(captor.getValue().profileId()).isEqualTo(profileId);
        then(captor.getValue().eventId()).isNotNull();
        verify(deckCardProjectionOutboxService).enqueueLive(profileId, ProfileProjectionOperation.DELETE);
    }
}
