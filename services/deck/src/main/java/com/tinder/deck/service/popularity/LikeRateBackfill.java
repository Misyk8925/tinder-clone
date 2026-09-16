package com.tinder.deck.service.popularity;

import com.tinder.deck.adapters.ProfileSwipeCountsHttp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rebuilds the like-rate counters from the durable swipe history.
 *
 * <p>Redis holds these counters and persists them, but a flush or a lost volume takes them
 * with it. Kafka cannot be replayed far enough — its retention is shorter than the history —
 * so the source of truth is the consumer service's {@code swipe_events} table, which has
 * every swipe ever recorded.
 *
 * <p>Until a rebuild runs, a lost counter degrades to every candidate scoring at the base
 * rate, which orders the deck exactly as the preference terms alone would. Safe, but blind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeRateBackfill {

    private final ProfileSwipeCountsHttp counts;
    private final LikeRateStore store;

    /**
     * Clears the counters and writes them again from history.
     *
     * <p>Clears first rather than merging: merging into a partial state would double-count
     * whatever survived, and a counter that is wrong in an unknown direction is worse than
     * one that is briefly empty.
     */
    public Mono<Result> run() {
        AtomicLong profiles = new AtomicLong();
        AtomicLong impressions = new AtomicLong();
        AtomicLong likes = new AtomicLong();

        return store.clear()
                .thenMany(counts.all())
                .concatMap(count -> {
                    UUID profileId;
                    try {
                        profileId = UUID.fromString(count.profileId());
                    } catch (IllegalArgumentException e) {
                        log.warn("Skipping unparseable profile id in swipe history: {}", count.profileId());
                        return Mono.empty();
                    }
                    profiles.incrementAndGet();
                    impressions.addAndGet(count.impressions());
                    likes.addAndGet(count.likes());
                    return store.restore(profileId, count.impressions(), count.likes());
                })
                .then(Mono.defer(() -> store.restoreTotals(impressions.get(), likes.get())))
                .then(Mono.fromCallable(() -> new Result(profiles.get(), impressions.get(), likes.get())))
                .doOnNext(result -> log.info("Like-rate backfill restored {} profiles, {} impressions, {} likes",
                        result.profiles(), result.impressions(), result.likes()));
    }

    public record Result(long profiles, long impressions, long likes) {
    }
}
