package com.tinder.profiles.infrastructure.messaging;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileDeletedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.profiles.application.profile.port.out.DomainEventPublisherPort;
import com.tinder.profiles.infrastructure.messaging.outbox.ProfileOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Messaging adapter implementing {@link DomainEventPublisherPort} on top of the
 * transactional outbox ({@link ProfileOutboxService}, unchanged). Builds the
 * versioned {@code tinder-contracts} event records — assigning the event id and
 * timestamp — so that boilerplate no longer lives in the application layer.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements DomainEventPublisherPort {

    private final ProfileOutboxService outboxService;

    @Override
    public void publishCreated(UUID profileId, String userId) {
        outboxService.enqueueProfileCreated(new ProfileCreatedEvent(
                UUID.randomUUID(), profileId, userId, Instant.now()));
    }

    @Override
    public void publishUpdated(UUID profileId, ChangeType changeType, Set<String> changedFields) {
        outboxService.enqueueProfileUpdated(new ProfileUpdatedEvent(
                UUID.randomUUID(),
                profileId,
                changeType,
                changedFields,
                Instant.now(),
                String.format("Profile updated: %s", changeType)));
    }

    @Override
    public void publishDeleted(UUID profileId) {
        outboxService.enqueueProfileDeleted(new ProfileDeletedEvent(
                UUID.randomUUID(), profileId, Instant.now()));
    }
}
