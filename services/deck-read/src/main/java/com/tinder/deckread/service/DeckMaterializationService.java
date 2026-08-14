package com.tinder.deckread.service;

import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.DeckMaterializationRequest;
import com.tinder.deckread.readmodel.DeckMaterializationRequestStore;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.redis.DeckRedisReader;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Worker-side materializer. Locks reduce duplicate work; revision CAS supplies correctness. */
@ApplicationScoped
public class DeckMaterializationService {

    @Inject
    DeckMaterializationRequestStore requests;

    @Inject
    DeckSnapshotStore locks;

    @Inject
    MaterializedDeckStore materialized;

    @Inject
    DeckRedisReader source;

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    ViewerMutationStore mutations;

    @Inject
    @RestClient
    DeckEnsureClient deckEnsure;

    private final Timer duration;
    private final Counter completed;
    private final Counter skipped;
    private final Counter fenced;
    private final Counter failed;
    private final DistributionSummary queueAge;

    @Inject
    public DeckMaterializationService(MeterRegistry meters) {
        this.duration = meters.timer("deck_read_materialization_duration");
        this.completed = meters.counter("deck_read_materializations", "outcome", "completed");
        this.skipped = meters.counter("deck_read_materializations", "outcome", "skipped");
        this.fenced = meters.counter("deck_read_materializations", "outcome", "fenced");
        this.failed = meters.counter("deck_read_materializations", "outcome", "failed");
        this.queueAge = meters.summary("deck_read_materialization_queue_age_ms");
    }

    public Uni<Void> materialize(DeckMaterializationRequest request) {
        Timer.Sample sample = Timer.start();
        queueAge.record(Math.max(0, java.time.Duration.between(
                request.requestedAt(), java.time.Instant.now()).toMillis()));
        return requests.requestedRevision(request.viewerProfileId())
                .flatMap(current -> {
                    if (current != request.requestedRevision()) {
                        skipped.increment();
                        return Uni.createFrom().voidItem();
                    }
                    return materialized.meta(request.viewerProfileId())
                            .flatMap(meta -> {
                                if (meta.isPresent()
                                        && meta.orElseThrow().publishedRevision() >= request.requestedRevision()) {
                                    skipped.increment();
                                    return Uni.createFrom().voidItem();
                                }
                                String token = UUID.randomUUID().toString();
                                return locks.acquireBuildLock(request.viewerProfileId(), token)
                                        .flatMap(acquired -> {
                                            if (!acquired) {
                                                skipped.increment();
                                                return Uni.createFrom().voidItem();
                                            }
                                            return build(request, token)
                                                    .eventually(() -> locks.releaseBuildLock(
                                                            request.viewerProfileId(), token));
                                        });
                            });
                })
                .onItem().invoke(ignored -> sample.stop(duration))
                .onFailure().call(error -> materialized.recordFailure(
                        request.viewerProfileId(), request.requestedRevision(), java.time.Instant.now()))
                .onFailure().invoke(error -> {
                    failed.increment();
                    sample.stop(duration);
                });
    }

    private Uni<Void> build(DeckMaterializationRequest request, String lockToken) {
        UUID viewer = request.viewerProfileId();
        return deckEnsure.ensure(viewer)
                .call(() -> locks.renewBuildLock(viewer, lockToken))
                .flatMap(ignored -> source.readStable(viewer, MaterializedDeckStore.TOTAL_WINDOW))
                .call(() -> locks.renewBuildLock(viewer, lockToken))
                .flatMap(snapshot -> {
                    List<UUID> ordered = snapshot.orderedProfileIds();
                    return Uni.combine().all().unis(
                                    profiles.cards(ordered),
                                    mutations.swiped(viewer, ordered),
                                    mutations.matched(viewer, ordered))
                            .asTuple()
                            .flatMap(tuple -> install(
                                    request,
                                    snapshot.buildTimestamp(),
                                    ordered,
                                    tuple.getItem1(),
                                    tuple.getItem2(),
                                    tuple.getItem3(),
                                    lockToken));
                });
    }

    private Uni<Void> install(
            DeckMaterializationRequest request,
            String sourceBuildTimestamp,
            List<UUID> ordered,
            Map<UUID, DeckCardDto> cards,
            Set<UUID> swiped,
            Set<UUID> matched,
            String lockToken
    ) {
        List<DeckCardDto> visible = new ArrayList<>();
        for (UUID profileId : ordered) {
            DeckCardDto card = cards.get(profileId);
            if (card != null && !swiped.contains(profileId) && !matched.contains(profileId)) {
                visible.add(card);
                if (visible.size() == MaterializedDeckStore.TOTAL_WINDOW) {
                    break;
                }
            }
        }
        DeckState state = visible.isEmpty() ? DeckState.EMPTY : DeckState.READY;
        return locks.renewBuildLock(request.viewerProfileId(), lockToken)
                .flatMap(ignored -> materialized.install(
                        request.viewerProfileId(), request.requestedRevision(), visible,
                        state, sourceBuildTimestamp, java.time.Instant.now()))
                .invoke(result -> {
                    if (result < 0) {
                        fenced.increment();
                    } else {
                        completed.increment();
                    }
                })
                .replaceWithVoid();
    }
}
