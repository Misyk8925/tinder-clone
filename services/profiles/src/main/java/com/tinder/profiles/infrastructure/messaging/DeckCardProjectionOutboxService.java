package com.tinder.profiles.infrastructure.messaging;

import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.profiles.infrastructure.messaging.outbox.ProfileOutboxService;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.List;

/** Enqueues full-card projections through the existing transactional outbox. */
@Component
@RequiredArgsConstructor
public class DeckCardProjectionOutboxService {

    private final ProfileRepository profileRepository;
    private final DeckCardProjectionFactory projectionFactory;
    private final ProfileOutboxService outboxService;

    public void enqueueLive(UUID profileId, ProfileProjectionOperation operation) {
        profileRepository.flush();
        outboxService.enqueueDeckCardProjection(projectionFactory.build(
                profileId, operation, ProjectionSource.LIVE, null));
    }

    public void enqueueAfterPhotoMutation(UUID profileId) {
        int updated = profileRepository.incrementAggregateVersion(profileId);
        if (updated != 1) {
            throw new IllegalStateException("Unable to increment profile version for photo mutation: " + profileId);
        }
        enqueueLive(profileId, ProfileProjectionOperation.UPSERT);
    }

    public ProfileDeckCardProjectionEvent buildBackfill(UUID profileId, UUID backfillRunId) {
        return projectionFactory.build(
                profileId, ProfileProjectionOperation.UPSERT, ProjectionSource.BACKFILL, backfillRunId);
    }

    public List<ProfileDeckCardProjectionEvent> buildBackfillPage(
            List<UUID> orderedProfileIds,
            UUID backfillRunId
    ) {
        return projectionFactory.buildBatch(
                orderedProfileIds,
                ProfileProjectionOperation.UPSERT,
                ProjectionSource.BACKFILL,
                backfillRunId);
    }
}
