package com.tinder.deck.service.popularity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How often each profile is liked by the people who are shown it.
 *
 * <p>Two counters rather than one. A raw like count answers "how many people liked them",
 * which a profile wins by having been on the platform longer: eight rounds of exposure beat
 * one regardless of quality. The rate answers "how often does a viewer who sees them like
 * them", and tenure sits in the denominator instead of the numerator. Measured offline, that
 * change doubled how well the counter tracked genuine appeal.
 *
 * <p>The keys stay inside deck rather than in {@code DeckRedisKeys}: that contract exists for
 * the deck hand-off to deck-read, and nothing outside this service reads these.
 */
@Component
@Slf4j
public class LikeRateStore {

    static final String SHOWS_KEY = "deck:likerate:shows";
    static final String LIKES_KEY = "deck:likerate:likes";
    // Running totals so density does not require reading every field of the hashes.
    static final String TOTAL_SHOWS_KEY = "deck:likerate:total:shows";
    static final String TOTAL_LIKES_KEY = "deck:likerate:total:likes";

    /**
     * Prior impressions and likes folded into every rate. It keeps a profile with two lucky
     * impressions from outranking one with two hundred, and gives a profile nobody has seen
     * the population's base rate instead of zero — so a newcomer is ranked low, not last.
     */
    private final double priorLikes;
    private final double priorShows;

    private final ReactiveHashOperations<String, String, String> hash;
    private final ReactiveStringRedisTemplate redis;

    public LikeRateStore(ReactiveStringRedisTemplate redis, PopularityProperties properties) {
        this.redis = redis;
        this.hash = redis.opsForHash();
        this.priorLikes = properties.getPriorLikes();
        this.priorShows = properties.getPriorShows();
    }

    /** Records one impression, and a like if the viewer swiped right. */
    public Mono<Void> observe(UUID profileId, boolean liked) {
        Mono<Void> counted = hash.increment(SHOWS_KEY, profileId.toString(), 1)
                .then(redis.opsForValue().increment(TOTAL_SHOWS_KEY))
                .then();
        if (!liked) {
            return counted;
        }
        return counted
                .then(hash.increment(LIKES_KEY, profileId.toString(), 1))
                .then(redis.opsForValue().increment(TOTAL_LIKES_KEY))
                .then();
    }

    /**
     * How much evidence the counters hold, which is the trigger FR-7 makes observable.
     *
     * <p>A factorisation of the swipe matrix only overtakes this counter at roughly eight
     * likes received per profile — measured offline, and a floor rather than an estimate,
     * because the screen handed the model its own generative form. Below that the counter is
     * the better ranker and far cheaper, so this number decides when the next feature starts.
     */
    public Mono<Density> density() {
        return Mono.zip(
                        hash.size(SHOWS_KEY).defaultIfEmpty(0L),
                        readTotal(TOTAL_SHOWS_KEY),
                        readTotal(TOTAL_LIKES_KEY))
                .map(values -> new Density(values.getT1(), values.getT2(), values.getT3()))
                .onErrorResume(error -> {
                    log.warn("Density lookup failed: {}", error.toString());
                    return Mono.just(new Density(0, 0, 0));
                });
    }

    private Mono<Long> readTotal(String key) {
        return redis.opsForValue().get(key)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    /**
     * Replaces the counters for one profile. Used only by a rebuild from the durable swipe
     * history, never on the live path.
     */
    public Mono<Void> restore(UUID profileId, long impressions, long likes) {
        return hash.put(SHOWS_KEY, profileId.toString(), Long.toString(impressions))
                .then(hash.put(LIKES_KEY, profileId.toString(), Long.toString(likes)))
                .then();
    }

    /** Clears everything, so a rebuild starts from a known state rather than merging into a partial one. */
    public Mono<Void> clear() {
        return redis.delete(SHOWS_KEY, LIKES_KEY, TOTAL_SHOWS_KEY, TOTAL_LIKES_KEY).then();
    }

    /** Sets the running totals after a rebuild has written every profile. */
    public Mono<Void> restoreTotals(long impressions, long likes) {
        return redis.opsForValue().set(TOTAL_SHOWS_KEY, Long.toString(impressions))
                .then(redis.opsForValue().set(TOTAL_LIKES_KEY, Long.toString(likes)))
                .then();
    }

    /**
     * @param profiles    profiles with at least one recorded impression
     * @param impressions total impressions across all of them
     * @param likes       total likes across all of them
     */
    public record Density(long profiles, long impressions, long likes) {

        public double likesPerProfile() {
            return profiles == 0 ? 0 : (double) likes / profiles;
        }

        public double impressionsPerProfile() {
            return profiles == 0 ? 0 : (double) impressions / profiles;
        }

        /**
         * Whether there is enough evidence per profile for a factorisation to be worth
         * building. Eight is the measured crossover, and a floor rather than a target.
         */
        public boolean readyForEmbeddings() {
            return likesPerProfile() >= EMBEDDING_THRESHOLD;
        }
    }

    public static final double EMBEDDING_THRESHOLD = 8.0;

    /**
     * Rates for a whole candidate pool in two round trips — one per counter — rather than
     * two per candidate. Deck builds score up to two thousand candidates at a time, so a
     * per-candidate lookup would put two thousand round trips on the build path.
     */
    public Mono<LikeRates> ratesFor(Collection<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Mono.just(LikeRates.empty(baseRate()));
        }
        List<String> fields = profileIds.stream().map(UUID::toString).toList();

        return Mono.zip(hash.multiGet(SHOWS_KEY, fields), hash.multiGet(LIKES_KEY, fields))
                .map(both -> combine(profileIds, both.getT1(), both.getT2()))
                .onErrorResume(error -> {
                    // NFR-3: an unavailable counter means every candidate scores at the base
                    // rate, which orders the deck exactly as the forward and mirror
                    // preference terms would. A deck built is better than a deck refused.
                    log.warn("Like-rate lookup failed, scoring at the base rate: {}", error.toString());
                    return Mono.just(LikeRates.empty(baseRate()));
                });
    }

    private LikeRates combine(Collection<UUID> profileIds, List<String> shows, List<String> likes) {
        Map<UUID, Double> rates = new HashMap<>(profileIds.size());
        Set<UUID> unseen = new HashSet<>();
        int index = 0;
        for (UUID profileId : profileIds) {
            double impressions = valueAt(shows, index);
            rates.put(profileId, rate(valueAt(likes, index), impressions));
            if (impressions == 0) {
                unseen.add(profileId);
            }
            index++;
        }
        return new LikeRates(rates, unseen, baseRate());
    }

    private double rate(double likes, double shows) {
        return (likes + priorLikes) / (shows + priorShows);
    }

    /** The rate of a profile with no recorded impressions at all. */
    public double baseRate() {
        return priorLikes / priorShows;
    }

    private static double valueAt(List<String> values, int index) {
        if (values == null || index >= values.size()) {
            return 0;
        }
        String value = values.get(index);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
