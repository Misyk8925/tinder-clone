package com.tinder.profiles.application.profile.port.out;

import com.tinder.contracts.event.v1.ChangeType;

import java.util.Set;
import java.util.UUID;

/**
 * Outbound port for publishing profile domain events. The implementing adapter
 * ({@code infrastructure.profile.messaging.OutboxEventPublisherAdapter}) builds
 * the versioned {@code tinder-contracts} event records (event id, timestamp,
 * description) and enqueues them via the transactional outbox — keeping that
 * boilerplate out of the application layer.
 *
 * <p>{@link ChangeType} comes from {@code tinder-contracts}, a pure shared
 * library, so it is an acceptable vocabulary type at this boundary.
 */
public interface DomainEventPublisherPort {

    void publishCreated(UUID profileId, String userId);

    void publishUpdated(UUID profileId, ChangeType changeType, Set<String> changedFields);

    void publishDeleted(UUID profileId);
}
