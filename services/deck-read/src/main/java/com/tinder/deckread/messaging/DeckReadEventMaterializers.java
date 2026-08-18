package com.tinder.deckread.messaging;

import com.tinder.contracts.event.v1.DeckBuiltEventV1;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.deckread.readmodel.HotViewerIndex;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.service.DeckMaterializationService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.time.Duration;
import java.util.function.Supplier;

/** Kafka materializers; acknowledgements happen only after Redis mutation succeeds. */
@ApplicationScoped
public class DeckReadEventMaterializers {

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    ViewerMutationStore viewerMutations;

    @Inject
    HotViewerIndex hotViewers;

    @Inject
    DeckMaterializationRequester requester;

    @Inject
    DeckMaterializationService materialization;

    @Incoming("profile-deck-card-projection")
    public Uni<Void> onProfileDeckCardProjection(ProfileDeckCardProjectionEvent event) {
        return withBoundedRetry(() -> profiles.apply(event))
                .flatMap(ignored -> hotViewers.viewers(event.profileId()))
                .flatMap(viewers -> {
                    java.util.LinkedHashSet<java.util.UUID> affected = new java.util.LinkedHashSet<>(viewers);
                    affected.add(event.profileId());
                    java.util.List<Throwable> fanOutFailures = new java.util.ArrayList<>();
                    MaterializationReason reason = event.operation() == ProfileProjectionOperation.DELETE
                            ? MaterializationReason.PROFILE_DELETED
                            : MaterializationReason.PROFILE_CHANGED;
                    return Multi.createFrom().iterable(affected)
                            .onItem().transformToUniAndConcatenate(viewer -> {
                                Uni<Void> suppression = event.operation() == ProfileProjectionOperation.DELETE
                                        ? viewerMutations.suppress(viewer, event.profileId())
                                        : Uni.createFrom().voidItem();
                                return suppression.flatMap(ignored -> requester.request(viewer, reason))
                                        .onFailure().recoverWithItem(error -> {
                                            fanOutFailures.add(error);
                                            return null;
                                        });
                            })
                            .collect().asList()
                            .flatMap(ignored -> fanOutFailures.isEmpty()
                                    ? Uni.createFrom().voidItem()
                                    : Uni.createFrom().failure(new IllegalStateException(
                                            "Profile materialization fan-out was only partially enqueued",
                                            fanOutFailures.get(0))));
                });
    }

    @Incoming("swipe-saved")
    public Uni<Void> onSwipeSaved(SwipeSavedEvent event) {
        return withBoundedRetry(() -> viewerMutations.applySwipe(event))
                .flatMap(ignored -> requester.request(
                        java.util.UUID.fromString(event.profile1Id()), MaterializationReason.SWIPE_SAVED));
    }

    @Incoming("match-created")
    public Uni<Void> onMatchCreated(MatchCreatedEvent event) {
        return withBoundedRetry(() -> viewerMutations.applyMatch(event))
                .flatMap(ignored -> Uni.combine().all().unis(
                                requester.request(
                                        java.util.UUID.fromString(event.profile1Id()),
                                        MaterializationReason.MATCH_CREATED),
                                requester.request(
                                        java.util.UUID.fromString(event.profile2Id()),
                                        MaterializationReason.MATCH_CREATED))
                        .discardItems());
    }

    @Incoming("deck-built")
    public Uni<Void> onDeckBuilt(DeckBuiltEventV1 event) {
        return requester.request(
                event.viewerProfileId(), MaterializationReason.DECK_BUILT, event.sourceBuildTimestamp());
    }

    @Incoming("materialization-requests-in")
    public Uni<Void> onMaterializationRequested(DeckMaterializationRequest request) {
        return withBoundedRetry(() -> materialization.materialize(request));
    }

    private Uni<Void> withBoundedRetry(Supplier<Uni<Void>> mutation) {
        return Uni.createFrom().<Void>deferred(mutation::get)
                .onFailure().retry()
                .withBackOff(Duration.ofMillis(25), Duration.ofMillis(100))
                .atMost(3);
    }
}
