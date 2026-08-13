package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshot;
import com.tinder.deckread.readmodel.DeckSnapshotMeta;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.redis.DeckRedisReader;
import com.tinder.deckread.redis.SourceDeckSnapshot;
import com.tinder.deckread.client.DeckEnsureClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Request-driven snapshot importer. The existing Deck service remains unchanged. */
@ApplicationScoped
public class DeckSnapshotBuilder {

    public static final int REPEAT_FALLBACK_DELAY_SECONDS = 30;
    public static final int REPEAT_FALLBACK_FAILURES = 2;
    public static final int MAX_FRESH = 500;
    public static final int MAX_REPEAT = 500;
    public static final int SOFT_FRESHNESS_MINUTES = 60;
    public static final int REPEAT_RETENTION_DAYS = 7;

    private static final Logger LOG = Logger.getLogger(DeckSnapshotBuilder.class);

    @Inject
    DeckSnapshotStore snapshots;

    @Inject
    DeckRedisReader sourceRedis;

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    ViewerMutationStore viewerMutations;

    @Inject
    ReadModelReadiness readiness;

    @Inject
    @RestClient
    DeckEnsureClient deckEnsure;

    /** Fire-and-observe: the Redis token lock deduplicates work across replicas. */
    public void requestBuild(UUID viewerProfileId) {
        build(viewerProfileId).subscribe().with(
                ignored -> { },
                failure -> LOG.warnf(failure, "Deck snapshot build failed for viewer %s", viewerProfileId));
    }

    Uni<Void> build(UUID viewerProfileId) {
        String token = UUID.randomUUID().toString();
        return snapshots.acquireBuildLock(viewerProfileId, token)
                .flatMap(acquired -> acquired
                        ? buildWithLock(viewerProfileId, token)
                                .eventually(() -> snapshots.releaseBuildLock(viewerProfileId, token))
                        : Uni.createFrom().voidItem());
    }

    private Uni<Void> buildWithLock(UUID viewerProfileId, String token) {
        Instant now = Instant.now();
        return snapshots.load(viewerProfileId)
                .flatMap(existing -> {
                    long previousGeneration = existing.map(snapshot -> snapshot.meta().generation()).orElse(0L);
                    return snapshots.markRefreshRequested(
                                    viewerProfileId, token, previousGeneration, now)
                            .flatMap(refreshMeta -> refreshMeta.isEmpty()
                                    ? Uni.createFrom().voidItem()
                                    : deckEnsure.ensure(viewerProfileId)
                                    .flatMap(ensured -> Boolean.TRUE.equals(ensured)
                                            ? sourceRedis.readStable(viewerProfileId, MAX_FRESH)
                                                    .flatMap(source -> installFresh(
                                                            viewerProfileId, previousGeneration, token, source, now))
                                            : resolveFalseEnsureAfterBuildWindow(
                                                    viewerProfileId, previousGeneration, token,
                                                    refreshMeta.orElseThrow(), now))
                                    .onFailure().recoverWithUni(failure -> recoverFromFailure(
                                            viewerProfileId, previousGeneration, token, existing,
                                            refreshMeta.orElseThrow(), now)));
                });
    }

    /**
     * The unchanged Deck endpoint returns {@code false} for both an empty build and writer-lock
     * contention. Keep BUILDING during the polling window, then accept the source only when its
     * stable build timestamp proves that Deck completed after this refresh started. A completed
     * empty build still has a timestamp and is materialized as EMPTY by {@link #installFresh}.
     */
    private Uni<Void> resolveFalseEnsureAfterBuildWindow(
            UUID viewerProfileId,
            long previousGeneration,
            String token,
            DeckSnapshotMeta refreshMeta,
            Instant now
    ) {
        if (!fallbackDelayElapsed(refreshMeta, now)) {
            return Uni.createFrom().voidItem();
        }
        return sourceRedis.readStable(viewerProfileId, MAX_FRESH)
                .flatMap(source -> sourceWasBuiltAfterRefresh(source, refreshMeta)
                        ? installFresh(viewerProfileId, previousGeneration, token, source, now)
                        : Uni.createFrom().failure(new IllegalStateException(
                                "Deck source timestamp predates the active refresh")));
    }

    private boolean sourceWasBuiltAfterRefresh(SourceDeckSnapshot source, DeckSnapshotMeta refreshMeta) {
        if (refreshMeta.refreshStartedAt() == null) {
            return false;
        }
        try {
            Instant sourceBuiltAt = Instant.ofEpochMilli(Long.parseLong(source.buildTimestamp()));
            return !sourceBuiltAt.isBefore(refreshMeta.refreshStartedAt());
        } catch (RuntimeException invalidTimestamp) {
            return false;
        }
    }

    private Uni<Void> installFresh(
            UUID viewerProfileId,
            long previousGeneration,
            String token,
            SourceDeckSnapshot source,
            Instant now
    ) {
        List<UUID> ordered = source.orderedProfileIds();
        return Uni.combine().all().unis(
                        profiles.cards(ordered),
                        viewerMutations.swiped(viewerProfileId, ordered),
                        viewerMutations.matched(viewerProfileId, ordered))
                .asTuple()
                .flatMap(tuple -> {
                    Map<UUID, ?> cards = tuple.getItem1();
                    Set<UUID> isSwiped = tuple.getItem2();
                    Set<UUID> matched = tuple.getItem3();
                    List<UUID> fresh = ordered.stream()
                            .filter(cards::containsKey)
                            .filter(id -> !isSwiped.contains(id))
                            .filter(id -> !matched.contains(id))
                            .limit(MAX_FRESH)
                            .toList();
                    DeckState state = fresh.isEmpty() ? DeckState.EMPTY : DeckState.READY;
                    return snapshots.install(
                            viewerProfileId, previousGeneration, token, fresh, List.of(), state,
                            source.buildTimestamp(), now).replaceWithVoid();
                });
    }

    private Uni<Void> recoverFromFailure(
            UUID viewerProfileId,
            long previousGeneration,
            String token,
            Optional<DeckSnapshot> existing,
            DeckSnapshotMeta refreshMeta,
            Instant now
    ) {
        return snapshots.recordFailure(viewerProfileId, token, previousGeneration, now)
                .flatMap(failureCount -> {
                    if (failureCount < 0) {
                        return Uni.createFrom().voidItem();
                    }
                    boolean fallbackAllowed = fallbackDelayElapsed(refreshMeta, now)
                            || failureCount >= REPEAT_FALLBACK_FAILURES;
                    if (!fallbackAllowed) {
                        return Uni.createFrom().voidItem();
                    }
                    return readiness.isRepeatReady()
                            .flatMap(repeatReady -> installFallbackOrUnavailable(
                                    viewerProfileId, previousGeneration, token, existing, repeatReady, now));
                });
    }

    private boolean fallbackDelayElapsed(DeckSnapshotMeta refreshMeta, Instant now) {
        Instant started = refreshMeta.refreshStartedAt();
        return started != null
                && !started.plusSeconds(REPEAT_FALLBACK_DELAY_SECONDS).isAfter(now);
    }

    private Uni<Void> installFallbackOrUnavailable(
            UUID viewerProfileId,
            long previousGeneration,
            String token,
            Optional<DeckSnapshot> existing,
            boolean repeatReady,
            Instant now
    ) {
        List<UUID> previousFresh = existing.map(DeckSnapshot::fresh).orElse(List.of());
        if (previousFresh.isEmpty() && !repeatReady) {
            return snapshots.markUnavailable(
                    viewerProfileId, token, previousGeneration, now).replaceWithVoid();
        }

        Uni<List<UUID>> repeatCandidates = repeatReady
                ? viewerMutations.repeatCandidates(viewerProfileId, MAX_REPEAT, now)
                : Uni.createFrom().item(List.of());
        return repeatCandidates
                .flatMap(repeat -> hydrateFallback(
                        viewerProfileId, previousGeneration, token, existing,
                        previousFresh, repeat, now));
    }

    private Uni<Void> hydrateFallback(
            UUID viewerProfileId,
            long previousGeneration,
            String token,
            Optional<DeckSnapshot> existing,
            List<UUID> previousFresh,
            List<UUID> repeat,
            Instant now
    ) {
        LinkedHashSet<UUID> combined = new LinkedHashSet<>(previousFresh);
        repeat.forEach(combined::add);
        List<UUID> candidates = List.copyOf(combined);
        if (candidates.isEmpty()) {
            return snapshots.markUnavailable(
                    viewerProfileId, token, previousGeneration, now).replaceWithVoid();
        }

        Uni<Set<UUID>> swipedFresh = previousFresh.isEmpty()
                ? Uni.createFrom().item(Set.of())
                : viewerMutations.swiped(viewerProfileId, previousFresh);
        return Uni.combine().all().unis(
                        profiles.cards(candidates),
                        swipedFresh,
                        viewerMutations.matched(viewerProfileId, candidates))
                .asTuple()
                .flatMap(tuple -> {
                    Map<UUID, ?> cards = tuple.getItem1();
                    Set<UUID> swiped = tuple.getItem2();
                    Set<UUID> matched = tuple.getItem3();
                    List<UUID> fresh = previousFresh.stream()
                            .filter(cards::containsKey)
                            .filter(id -> !swiped.contains(id))
                            .filter(id -> !matched.contains(id))
                            .limit(MAX_FRESH)
                            .toList();
                    Set<UUID> freshSet = Set.copyOf(fresh);
                    List<UUID> eligibleRepeat = repeat.stream()
                            .filter(cards::containsKey)
                            .filter(id -> !matched.contains(id))
                            .filter(id -> !freshSet.contains(id))
                            .limit(MAX_REPEAT)
                            .toList();
                    if (fresh.isEmpty() && eligibleRepeat.isEmpty()) {
                        return snapshots.markUnavailable(
                                viewerProfileId, token, previousGeneration, now).replaceWithVoid();
                    }
                    String sourceBuildTimestamp = existing
                            .map(snapshot -> snapshot.meta().sourceBuildTimestamp())
                            .orElse("");
                    return snapshots.install(
                            viewerProfileId, previousGeneration, token, fresh, eligibleRepeat,
                            DeckState.DEGRADED, sourceBuildTimestamp, now).replaceWithVoid();
                });
    }
}
