package com.tinder.deckread.messaging;

import com.tinder.deckread.readmodel.DeckMaterializationRequestStore;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

/** Durable-enough request path: Redis revision first, keyed Kafka notification second. */
@ApplicationScoped
public class DeckMaterializationRequester {

    private static final Duration API_NOTIFICATION_RETRY_INTERVAL = Duration.ofSeconds(5);

    @Inject
    DeckMaterializationRequestStore requests;

    @Inject
    @Channel("materialization-requests-out")
    MutinyEmitter<DeckMaterializationRequest> emitter;

    public Uni<Void> request(UUID viewerProfileId, MaterializationReason reason) {
        return request(viewerProfileId, reason, "");
    }

    public Uni<Void> request(
            UUID viewerProfileId,
            MaterializationReason reason,
            String sourceBuildTimestamp
    ) {
        Instant now = Instant.now();
        Uni<DeckMaterializationRequestStore.RequestAllocation> allocation =
                reason == MaterializationReason.API_MISS || reason == MaterializationReason.API_STALE
                        ? requests.requestCoalesced(
                                viewerProfileId, reason.name(), now, API_NOTIFICATION_RETRY_INTERVAL)
                        : requests.request(viewerProfileId, reason.name(), now)
                                .map(revision -> new DeckMaterializationRequestStore.RequestAllocation(
                                        revision, true));
        return allocation.flatMap(result -> {
                    if (!result.enqueue()) {
                        return Uni.createFrom().voidItem();
                    }
                    DeckMaterializationRequest request = new DeckMaterializationRequest(
                            UUID.randomUUID(), viewerProfileId, result.revision(), reason,
                            sourceBuildTimestamp == null ? "" : sourceBuildTimestamp, now);
                    Message<DeckMaterializationRequest> message = Message.of(request)
                            .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                                    .withKey(viewerProfileId.toString())
                                    .build());
                    return emitter.sendMessage(message);
                });
    }
}
