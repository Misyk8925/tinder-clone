package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckCardV1Dto;
import com.tinder.deckread.dto.DeckPage;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshot;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.smallrye.mutiny.Uni;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.tinder.deckread.messaging.MaterializationReason;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client queries backed only by the Deck Read Redis projection. */
@ApplicationScoped
public class DeckQueryService {

    public static final String DECK_TEMPORARILY_UNAVAILABLE = "DECK_TEMPORARILY_UNAVAILABLE";
    public static final String INVALID_CURSOR = "INVALID_CURSOR";
    public static final String INVALID_LIMIT = "INVALID_LIMIT";
    public static final String INVALID_PAGINATION = "INVALID_PAGINATION";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String READ_MODEL_NOT_READY = "READ_MODEL_NOT_READY";

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    DeckSnapshotStore snapshots;

    @Inject
    ViewerMutationStore viewerMutations;

    @Inject
    ReadModelReadiness readiness;

    @Inject
    DeckSnapshotBuilder builder;

    @Inject
    DeckCursorCodec cursors;

    @Inject
    MaterializedDeckQuery materializedQuery;

    @Inject
    DeckRefreshTrigger refreshes;

    @Inject
    MeterRegistry meters;

    @ConfigProperty(name = "deck-read.materialized.required", defaultValue = "false")
    boolean materializedRequired;

    public Uni<Boolean> isReadModelReady() {
        return readiness.isReady();
    }

    public Uni<DeckQueryResult> getDeckV2(String viewerUserId, String cursor, int limit) {
        if (cursor != null) {
            try {
                cursors.decode(cursor);
            } catch (DeckCursorCodec.InvalidCursorException invalid) {
                return Uni.createFrom().item(new DeckQueryResult.Failure(
                        400, INVALID_CURSOR, "Invalid deck cursor",
                        "The cursor is malformed or cannot be verified."));
            }
        }
        return readiness.isReady()
                .flatMap(ready -> ready
                        ? profiles.viewerProfileId(viewerUserId)
                        : Uni.createFrom().item((UUID) null))
                .flatMap(viewerProfileId -> {
                    if (viewerProfileId == null) {
                        return readiness.isReady().map(ready -> ready
                                ? new DeckQueryResult.Building()
                                : failureNotReady());
                    }
                    DeckCursorCodec.Cursor decoded = cursor == null ? null : cursors.decode(cursor);
                    long requestedGeneration = decoded == null ? 0 : decoded.generation();
                    int requestedPosition = decoded == null ? 0 : decoded.position();
                    return materializedQuery.getV2(
                                    viewerProfileId, requestedGeneration, requestedPosition, limit)
                            .flatMap(result -> {
                        if (result.isPresent()) {
                            DeckQueryResult hit = result.orElseThrow();
                            recordPath(materializedPath(hit, requestedPosition));
                            return Uni.createFrom().item(hit);
                        }
                        recordPath("miss");
                        requestMaterialization(viewerProfileId, MaterializationReason.API_MISS);
                        if (materializedRequired) {
                            return Uni.createFrom().item(new DeckQueryResult.Building());
                        }
                        return snapshots.load(viewerProfileId)
                                .flatMap(snapshot -> snapshot.isEmpty()
                                        ? requestAndBuild(viewerProfileId)
                                        : page(viewerProfileId, snapshot.get(), cursor, limit));
                    });
                })
                .onFailure(DeckCursorCodec.InvalidCursorException.class)
                .recoverWithItem(new DeckQueryResult.Failure(
                        400, INVALID_CURSOR, "Invalid deck cursor",
                        "The cursor is malformed or cannot be verified."))
                .onFailure().recoverWithItem(failureNotReady());
    }

    public Uni<List<DeckCardV1Dto>> getDeckV1(
            String viewerUserId, int offset, int limit) {
        return profiles.viewerProfileId(viewerUserId)
                .flatMap(viewerProfileId -> {
                    if (viewerProfileId == null) {
                        return Uni.createFrom().item(List.<DeckCardV1Dto>of());
                    }
                    return materializedQuery.getV1(viewerProfileId, offset, limit).flatMap(cards -> {
                        if (cards.isPresent()) {
                            recordPath(offset >= MaterializedDeckStore.READY_WINDOW
                                    ? "deep" : "fast");
                            return Uni.createFrom().item(cards.orElseThrow());
                        }
                        recordPath("miss");
                        requestMaterialization(viewerProfileId, MaterializationReason.API_MISS);
                        if (materializedRequired) {
                            return Uni.createFrom().item(List.of());
                        }
                        return snapshots.load(viewerProfileId)
                            .flatMap(snapshot -> {
                                if (snapshot.isEmpty()) {
                                    builder.requestBuild(viewerProfileId);
                                    return Uni.createFrom().item(List.<DeckCardV1Dto>of());
                                }
                                return scanVisible(viewerProfileId, ordered(snapshot.get()), 0, offset + limit)
                                        .map(result -> result.cards().stream()
                                                .skip(offset)
                                                .limit(limit)
                                                .map(DeckCardV1Dto::from)
                                                .toList());
                            });
                    });
                });
    }

    private void requestMaterialization(UUID viewerProfileId, MaterializationReason reason) {
        refreshes.request(viewerProfileId, reason);
    }

    private void recordPath(String path) {
        if (meters != null) {
            meters.counter("deck_read_requests", "path", path).increment();
        }
    }

    private String materializedPath(DeckQueryResult result, int requestedPosition) {
        if (result instanceof DeckQueryResult.Failure) {
            return "miss";
        }
        if (result instanceof DeckQueryResult.Page page
                && !page.value().cursorReset()
                && requestedPosition >= MaterializedDeckStore.READY_WINDOW) {
            return "deep";
        }
        return "fast";
    }

    private Uni<DeckQueryResult> requestAndBuild(UUID viewerProfileId) {
        builder.requestBuild(viewerProfileId);
        return Uni.createFrom().item(new DeckQueryResult.Building());
    }

    private Uni<DeckQueryResult> page(
            UUID viewerProfileId,
            DeckSnapshot snapshot,
            String cursor,
            int limit
    ) {
        if (snapshot.meta().unavailable()) {
            builder.requestBuild(viewerProfileId);
            return Uni.createFrom().item(new DeckQueryResult.Failure(
                    503, DECK_TEMPORARILY_UNAVAILABLE, "Deck temporarily unavailable",
                    "No fresh or safely repeatable cards are available."));
        }

        boolean stale = snapshot.meta().builtAt() != null
                && snapshot.meta().builtAt().plus(Duration.ofMinutes(DeckSnapshotStore.SOFT_FRESHNESS_MINUTES))
                .isBefore(Instant.now());
        if (stale) {
            builder.requestBuild(viewerProfileId);
        }

        DeckCursorCodec.Cursor decoded = cursor == null ? null : cursors.decode(cursor);
        boolean cursorReset = decoded != null && decoded.generation() != snapshot.meta().generation();
        int position = decoded == null || cursorReset ? 0 : decoded.position();

        List<Candidate> ordered = ordered(snapshot);
        int start = Math.min(position, ordered.size());
        return scanVisible(viewerProfileId, ordered, start, limit)
                .map(result -> {
                    String next = result.nextPosition() < ordered.size()
                            ? cursors.encode(snapshot.meta().generation(), result.nextPosition())
                            : null;
                    DeckState state = stale ? DeckState.REFRESHING : snapshot.meta().state();
                    if (result.cards().isEmpty() && start == 0 && next == null && state != DeckState.DEGRADED) {
                        state = DeckState.EMPTY;
                    }
                    return (DeckQueryResult) new DeckQueryResult.Page(new DeckPage(
                            result.cards(), next, snapshot.meta().generation(), cursorReset, state));
                });
    }

    private List<Candidate> ordered(DeckSnapshot snapshot) {
        LinkedHashMap<UUID, Candidate> ordered = new LinkedHashMap<>();
        snapshot.fresh().forEach(id -> ordered.putIfAbsent(id, new Candidate(id, true)));
        snapshot.repeat().forEach(id -> ordered.putIfAbsent(id, new Candidate(id, false)));
        return List.copyOf(ordered.values());
    }

    private Uni<ScanResult> scanVisible(
            UUID viewerProfileId,
            List<Candidate> ordered,
            int position,
            int limit
    ) {
        return scanVisible(viewerProfileId, ordered, position, limit, List.of());
    }

    private Uni<ScanResult> scanVisible(
            UUID viewerProfileId,
            List<Candidate> ordered,
            int position,
            int limit,
            List<DeckCardDto> accumulated
    ) {
        if (position >= ordered.size() || accumulated.size() >= limit) {
            return Uni.createFrom().item(new ScanResult(accumulated, position));
        }

        int batchEnd = Math.min(position + Math.min(100, Math.max(limit, 20)), ordered.size());
        List<Candidate> batch = ordered.subList(position, batchEnd);
        List<UUID> ids = batch.stream().map(Candidate::profileId).toList();
        List<UUID> freshIds = batch.stream()
                .filter(Candidate::fresh)
                .map(Candidate::profileId)
                .toList();

        return Uni.combine().all().unis(
                        profiles.cards(ids),
                        viewerMutations.swiped(viewerProfileId, freshIds),
                        viewerMutations.matched(viewerProfileId, ids))
                .asTuple()
                .flatMap(tuple -> {
                    Map<UUID, DeckCardDto> cards = tuple.getItem1();
                    Set<UUID> isSwiped = tuple.getItem2();
                    Set<UUID> matched = tuple.getItem3();
                    List<DeckCardDto> visible = new java.util.ArrayList<>(accumulated);
                    int nextPosition = position;
                    for (Candidate candidate : batch) {
                        nextPosition++;
                        DeckCardDto card = cards.get(candidate.profileId());
                        if (card != null
                                && !matched.contains(candidate.profileId())
                                && (!candidate.fresh() || !isSwiped.contains(candidate.profileId()))) {
                            visible.add(card);
                            if (visible.size() == limit) {
                                break;
                            }
                        }
                    }
                    List<DeckCardDto> result = List.copyOf(visible);
                    if (result.size() >= limit || nextPosition >= ordered.size()) {
                        return Uni.createFrom().item(new ScanResult(result, nextPosition));
                    }
                    return scanVisible(viewerProfileId, ordered, nextPosition, limit, result);
                });
    }

    private DeckQueryResult failureNotReady() {
        return new DeckQueryResult.Failure(
                503, READ_MODEL_NOT_READY, "Deck read model is recovering",
                "Profile backfill and event catch-up have not completed.");
    }

    private record Candidate(UUID profileId, boolean fresh) {
    }

    private record ScanResult(List<DeckCardDto> cards, int nextPosition) {
    }
}
