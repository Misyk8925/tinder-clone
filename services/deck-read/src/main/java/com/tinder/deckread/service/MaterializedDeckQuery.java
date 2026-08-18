package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckCardV1Dto;
import com.tinder.deckread.dto.DeckPage;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.MaterializationReason;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckSlice;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Queries the immutable-generation materialized read model, including its bounded deep-page path. */
@ApplicationScoped
public class MaterializedDeckQuery {

    private final MaterializedDeckStore store;
    private final ProfileProjectionStore profiles;
    private final ViewerMutationStore viewerMutations;
    private final DeckCursorCodec cursors;
    private final DeckRefreshTrigger refreshes;

    @Inject
    public MaterializedDeckQuery(
            MaterializedDeckStore store,
            ProfileProjectionStore profiles,
            ViewerMutationStore viewerMutations,
            DeckCursorCodec cursors,
            DeckRefreshTrigger refreshes
    ) {
        this.store = store;
        this.profiles = profiles;
        this.viewerMutations = viewerMutations;
        this.cursors = cursors;
        this.refreshes = refreshes;
    }

    public Uni<Optional<DeckQueryResult>> getV2(
            UUID viewerProfileId, long requestedGeneration, int requestedPosition, int limit) {
        return store.readPage(viewerProfileId, requestedGeneration, requestedPosition, limit)
                .flatMap(slice -> slice == null
                        ? Uni.createFrom().item(Optional.empty())
                        : page(viewerProfileId, slice, requestedPosition, limit).map(Optional::of));
    }

    public Uni<Optional<List<DeckCardV1Dto>>> getV1(UUID viewerProfileId, int offset, int limit) {
        return store.readPage(viewerProfileId, 0, offset, limit)
                .flatMap(slice -> {
                    if (slice == null) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    requestIfStale(viewerProfileId, slice);
                    if (slice.cards().isEmpty()
                            && offset >= MaterializedDeckStore.READY_WINDOW
                            && offset < slice.totalCount()) {
                        refreshes.request(viewerProfileId, MaterializationReason.API_STALE);
                        return deepCards(viewerProfileId, slice, offset, limit)
                                .map(cards -> Optional.of(cards.stream().map(DeckCardV1Dto::from).toList()));
                    }
                    return Uni.createFrom().item(Optional.of(
                            slice.cards().stream().map(DeckCardV1Dto::from).toList()));
                });
    }

    private Uni<DeckQueryResult> page(
            UUID viewerProfileId, MaterializedDeckSlice slice, int requestedPosition, int limit) {
        if (slice.unavailable()) {
            refreshes.request(viewerProfileId, MaterializationReason.API_STALE);
            return Uni.createFrom().item(new DeckQueryResult.Failure(
                    503, DeckQueryService.DECK_TEMPORARILY_UNAVAILABLE, "Deck temporarily unavailable",
                    "No fresh or safely repeatable cards are available."));
        }
        int position = slice.cursorReset() ? 0 : requestedPosition;
        if (slice.cards().isEmpty()
                && position >= MaterializedDeckStore.READY_WINDOW
                && position < slice.totalCount()) {
            return deepPage(viewerProfileId, slice, position, limit);
        }
        boolean stale = requestIfStale(viewerProfileId, slice);
        String next = slice.nextPosition() < slice.totalCount()
                ? cursors.encode(slice.generation(), slice.nextPosition())
                : null;
        DeckState state = stale ? DeckState.REFRESHING : slice.state();
        if (slice.cards().isEmpty() && position == 0 && next == null && state != DeckState.DEGRADED) {
            state = DeckState.EMPTY;
        }
        return Uni.createFrom().item(new DeckQueryResult.Page(new DeckPage(
                slice.cards(), next, slice.generation(), slice.cursorReset(), state)));
    }

    private Uni<DeckQueryResult> deepPage(
            UUID viewerProfileId, MaterializedDeckSlice slice, int position, int limit) {
        return deepCards(viewerProfileId, slice, position, limit).map(cards -> {
            int tailOffset = Math.max(0, position - MaterializedDeckStore.READY_WINDOW);
            int fetched = Math.min(
                    Math.min(100, Math.max(limit, 20)),
                    Math.max(0, slice.totalCount() - MaterializedDeckStore.READY_WINDOW - tailOffset));
            int nextPosition = Math.min(slice.totalCount(), position + fetched);
            String next = nextPosition < slice.totalCount()
                    ? cursors.encode(slice.generation(), nextPosition)
                    : null;
            refreshes.request(viewerProfileId, MaterializationReason.API_STALE);
            return (DeckQueryResult) new DeckQueryResult.Page(new DeckPage(
                    cards, next, slice.generation(), slice.cursorReset(), slice.state()));
        });
    }

    private Uni<List<DeckCardDto>> deepCards(
            UUID viewerProfileId, MaterializedDeckSlice slice, int position, int limit) {
        int tailOffset = Math.max(0, position - MaterializedDeckStore.READY_WINDOW);
        int fetch = Math.min(100, Math.max(limit, 20));
        return store.readTail(viewerProfileId, slice.generation(), tailOffset, fetch)
                .flatMap(ids -> Uni.combine().all().unis(
                                profiles.cards(ids),
                                viewerMutations.swiped(viewerProfileId, ids),
                                viewerMutations.matched(viewerProfileId, ids))
                        .asTuple()
                        .map(tuple -> ids.stream()
                                .filter(id -> tuple.getItem1().containsKey(id))
                                .filter(id -> !tuple.getItem2().contains(id))
                                .filter(id -> !tuple.getItem3().contains(id))
                                .limit(limit)
                                .map(tuple.getItem1()::get)
                                .toList()));
    }

    private boolean requestIfStale(UUID viewerProfileId, MaterializedDeckSlice slice) {
        boolean stale = slice.builtAt() != null
                && slice.builtAt().plus(Duration.ofMinutes(DeckSnapshotStore.SOFT_FRESHNESS_MINUTES))
                .isBefore(Instant.now());
        if (stale) {
            refreshes.request(viewerProfileId, MaterializationReason.API_STALE);
        }
        return stale;
    }
}
