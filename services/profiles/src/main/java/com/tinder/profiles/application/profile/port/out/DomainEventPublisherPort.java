package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.domain.profile.ProfileChangeType;

import java.util.Set;
import java.util.UUID;

/**
 * Outbound port for publishing profile domain events. The implementing adapter
 * ({@code infrastructure.profile.messaging.OutboxEventPublisherAdapter}) builds
 * the versioned {@code tinder-contracts} event records (event id, timestamp,
 * description) and enqueues them via the transactional outbox — keeping that
 * boilerplate out of the application layer.
 *
 * <p>The adapter translates the domain change type to the versioned integration
 * event contract.
 */
public interface DomainEventPublisherPort {

    void publishCreated(UUID profileId, String userId);

    void publishUpdated(UUID profileId, ProfileChangeType changeType, Set<String> changedFields);

    void publishDeleted(UUID profileId);
}
