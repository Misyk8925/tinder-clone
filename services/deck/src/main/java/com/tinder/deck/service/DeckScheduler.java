package com.tinder.deck.service;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background scheduler that rebuilds decks for active users.
 * Runs periodically to keep decks fresh in Redis cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeckScheduler {

    private final DeckService deckService;
    private final ProfilesHttp profilesHttp;
    private final DeckCache deckCache;

    /** Maximum number of deck rebuilds to run in parallel */
    @Value("${deck.scheduler.max-concurrent-rebuilds:10}")
    private int maxConcurrentRebuilds;

    /** Per-user rebuild timeout in seconds */
    @Value("${deck.scheduler.user-rebuild-timeout-seconds:30}")
    private int userRebuildTimeoutSeconds;

    /** How far back a viewer must have requested a deck to be eligible for scheduled rebuilds */
    @Value("${deck.scheduler.recent-viewers-window-minutes:30}")
    private int recentViewersWindowMinutes;

    /** Upper bound for viewers considered in a single scheduled pass */
    @Value("${deck.scheduler.max-recent-viewers:1000}")
    private int maxRecentViewers;

    /**
     * Rebuild decks for all active users in parallel.
     * Uses flatMap with a concurrency cap so we never overwhelm downstream services.
     *
     * <p>With Preferences Cache enabled this is much more efficient:
     * <ul>
     *   <li>Groups users by preferences (10–15 groups typically)</li>
     *   <li>Shares candidate queries across users with same preferences</li>
     *   <li>~100x fewer database queries vs individual rebuilds</li>
     * </ul>
     */
    @Scheduled(cron = "${deck.scheduler.cron:0 0/1 * * * *}")
    public void rebuildAllDecks() {
        log.info("Starting scheduled rebuild for recent viewers (window={}m, max={}, concurrency={})",
                recentViewersWindowMinutes, maxRecentViewers, maxConcurrentRebuilds);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);

        deckCache.getRecentViewerIds(Duration.ofMinutes(recentViewersWindowMinutes), maxRecentViewers)
                .buffer(100)
                .concatMap(viewerIds -> {
                    if (viewerIds.isEmpty()) {
                        return Flux.empty();
                    }
                    return profilesHttp.getProfilesByIds(viewerIds);
                })
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.error("Failed to fetch recent viewers for scheduled rebuild: {}", e.getMessage());
                    return Flux.empty();
                })
                .flatMap(viewer -> rebuildDeckForUserReactive(viewer)
                        .doOnSuccess(v -> success.incrementAndGet())
                        .doOnError(e -> failure.incrementAndGet())
                        .onErrorResume(e -> Mono.empty()),
                        maxConcurrentRebuilds)
                .doOnComplete(() ->
                        log.info("Scheduled recent-viewer deck rebuild completed — success={}, failed={}",
                                success.get(), failure.get()))
                .subscribe();
    }

    /**
     * Rebuild deck for a single user (reactive, for use in the scheduler pipeline).
     */
    public Mono<Void> rebuildDeckForUserReactive(SharedProfileDto viewer) {
        if (viewer == null || viewer.id() == null) {
            log.error("Cannot rebuild deck: viewer or viewer ID is null");
            return Mono.empty();
        }

        log.debug("Rebuilding deck for user: {} (name: {})", viewer.id(), viewer.name());

        return deckService.rebuildOneDeck(viewer)
                .timeout(Duration.ofSeconds(userRebuildTimeoutSeconds))
                .doOnSuccess(v -> log.debug("Successfully rebuilt deck for user: {}", viewer.id()))
                .doOnError(e -> log.error("Failed to rebuild deck for user: {} — {}",
                        viewer.id(), e.getMessage()));
    }

    /**
     * Rebuild deck for a single user (fire-and-forget, for external callers).
     */
    public void rebuildDeckForUser(SharedProfileDto viewer) {
        rebuildDeckForUserReactive(viewer).subscribe();
    }
}
