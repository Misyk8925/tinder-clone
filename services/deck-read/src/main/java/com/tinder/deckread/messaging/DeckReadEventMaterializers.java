package com.tinder.deckread.messaging;

import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
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

    @Incoming("profile-deck-card-projection")
    public Uni<Void> onProfileDeckCardProjection(ProfileDeckCardProjectionEvent event) {
        return withBoundedRetry(() -> profiles.apply(event));
    }

    @Incoming("swipe-saved")
    public Uni<Void> onSwipeSaved(SwipeSavedEvent event) {
        return withBoundedRetry(() -> viewerMutations.applySwipe(event));
    }

    @Incoming("match-created")
    public Uni<Void> onMatchCreated(MatchCreatedEvent event) {
        return withBoundedRetry(() -> viewerMutations.applyMatch(event));
    }

    private Uni<Void> withBoundedRetry(Supplier<Uni<Void>> mutation) {
        return Uni.createFrom().<Void>deferred(mutation::get)
                .onFailure().retry()
                .withBackOff(Duration.ofMillis(25), Duration.ofMillis(100))
                .atMost(3);
    }
}
