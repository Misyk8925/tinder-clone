package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.smallrye.mutiny.Uni;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Appends eligible 7-day PASS/LIKE repeats after unseen cards on a successful rebuild. */
final class DeckRepeatFill {

    private DeckRepeatFill() {
    }

    record Result(List<UUID> ids, List<DeckCardDto> cards) {
        static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    static DeckState state(boolean hasFresh, boolean hasRepeat) {
        if (hasFresh) {
            return DeckState.READY;
        }
        if (hasRepeat) {
            return DeckState.DEGRADED;
        }
        return DeckState.EMPTY;
    }

    static Uni<Result> eligible(
            ReadModelReadiness readiness,
            ViewerMutationStore mutations,
            ProfileProjectionStore profiles,
            UUID viewerProfileId,
            Collection<UUID> alreadyIncluded,
            int maxRepeat,
            Instant now
    ) {
        if (maxRepeat <= 0) {
            return Uni.createFrom().item(Result.empty());
        }
        return readiness.isRepeatReady().flatMap(repeatReady -> {
            if (!Boolean.TRUE.equals(repeatReady)) {
                return Uni.createFrom().item(Result.empty());
            }
            return mutations.repeatCandidates(viewerProfileId, maxRepeat, now)
                    .flatMap(repeatIds -> {
                        Set<UUID> excluded = Set.copyOf(alreadyIncluded);
                        List<UUID> needed = repeatIds.stream()
                                .filter(id -> !excluded.contains(id))
                                .limit(maxRepeat)
                                .toList();
                        if (needed.isEmpty()) {
                            return Uni.createFrom().item(Result.empty());
                        }
                        return Uni.combine().all().unis(
                                        profiles.cards(needed),
                                        mutations.matched(viewerProfileId, needed))
                                .asTuple()
                                .map(tuple -> {
                                    List<UUID> ids = needed.stream()
                                            .filter(id -> tuple.getItem1().containsKey(id))
                                            .filter(id -> !tuple.getItem2().contains(id))
                                            .toList();
                                    List<DeckCardDto> cards = ids.stream()
                                            .map(tuple.getItem1()::get)
                                            .toList();
                                    return new Result(ids, cards);
                                });
                    });
        });
    }
}
