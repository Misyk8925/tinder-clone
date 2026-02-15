package com.tinder.deck.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckCache {

    private final ReactiveStringRedisTemplate redis;

    // Redis key patterns
    private static String deckKey(UUID id)        { return "deck:" + id; }
    private static String deckTsKey(UUID id)      { return "deck:build:ts:" + id; }
    private static String staleKey(UUID viewerId) { return "deck:stale:" + viewerId; }
    private static String lockKey(UUID viewerId)  { return "deck:lock:" + viewerId; }
    // Matches only primary deck data keys of the form "deck:{uuid}" and intentionally
    // excludes other "deck:"-prefixed keys such as "deck:build:ts:*", "deck:stale:*",
    // and "deck:lock:*".
    private static final Pattern DECK_KEY_PATTERN = Pattern.compile("^deck:([0-9a-fA-F-]{36})$");
    private static String preferencesKey(int minAge, int maxAge, String gender) {
        return String.format("prefs:%d:%d:%s", minAge, maxAge, gender.toUpperCase());
    }

    // Lock configuration
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(30);
    private static final String LOCK_VALUE = "locked";

    // Stale tracking configuration
    private static final Duration DEFAULT_STALE_TTL = Duration.ofHours(24);

    // Preferences cache configuration
    @Value("${deck.preferences-cache-ttl-minutes:5}")
    private long preferencesCacheTtlMinutes;


    public Mono<Void> writeDeck(UUID viewerId, List<Entry<UUID, Double>> deck, Duration ttl) {
        String key   = deckKey(viewerId);
        String tsKey = deckTsKey(viewerId);

        ReactiveZSetOperations<String, String> z = redis.opsForZSet();

        Mono<Long> addAll = Flux.fromIterable(deck)
                .map(e -> ZSetOperations.TypedTuple.of(e.getKey().toString(), e.getValue()))
                .collect(Collectors.toSet())
                .flatMap(tuples -> z.addAll(key, tuples));

        return redis.delete(key, tsKey)     // fast delete старых данных
                .then(addAll)                               // ZADD all
                .then(redis.expire(key, ttl))               // TTL
                .then(redis.opsForValue().set(tsKey, String.valueOf(System.currentTimeMillis()))) // TS
                .then();
    }

    public Flux<UUID> readDeck(UUID viewerId, int offset, int limit) {
        String key = deckKey(viewerId);
        long end = offset + Math.max(limit, 1) - 1;
        return redis.opsForZSet()
                .reverseRange(key, org.springframework.data.domain.Range.closed((long)offset, end))
                .map(UUID::fromString);
    }

    public Mono<Long> size(UUID viewerId) {
        return redis.opsForZSet().size(deckKey(viewerId));
    }

    public Mono<Optional<Instant>> getBuildInstant(UUID viewerId) {
        return redis.opsForValue().get(deckTsKey(viewerId))
                .map(v -> Optional.of(Instant.ofEpochMilli(Long.parseLong(v))))
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Long> invalidate(UUID viewerId) {
        return redis.delete(deckKey(viewerId), deckTsKey(viewerId));
    }

    public Mono<List<UUID>> readTop(UUID viewerId, int topN) {
        return readDeck(viewerId, 0, topN).collectList();
    }

    public Flux<Entry<UUID, Double>> readRangeWithScores(UUID viewerId, long start, long end) {
        return redis.opsForZSet()
                .reverseRangeWithScores(deckKey(viewerId), org.springframework.data.domain.Range.closed(start, end))
                .map(t -> Map.entry(UUID.fromString(Objects.requireNonNull(t.getValue())),
                        Objects.requireNonNull(t.getScore())));
    }

    // ==================== Phase 2: Stale Tracking ====================

    /**
     * Mark a profile as stale in viewer's deck
     * Stale profiles should be filtered out or rebuilt
     *
     * @param viewerId The viewer whose deck contains the profile
     * @param profileId The profile that became stale (e.g., age/gender changed)
     * @return Mono that completes when profile is marked as stale
     */
    public Mono<Long> markAsStale(UUID viewerId, UUID profileId) {
        String key = staleKey(viewerId);
        log.debug("Marking profile {} as stale for viewer {}", profileId, viewerId);

        return redis.opsForSet()
                .add(key, profileId.toString())
                .flatMap(added -> redis.expire(key, DEFAULT_STALE_TTL).thenReturn(added));
    }

    /**
     * Mark a profile as stale across all cached decks.
     *
     * @param profileId The profile that became stale (e.g., age/gender changed)
     * @return Mono<Long> number of decks marked as stale
     */
    public Mono<Long> markAsStaleForAllDecks(UUID profileId) {
        return redis.keys("deck:*")
                .filter(key -> DECK_KEY_PATTERN.matcher(key).matches())
                .flatMap(key -> {
                    String idPart = key.substring("deck:".length());
                    try {
                        return Mono.just(UUID.fromString(idPart));
                    } catch (IllegalArgumentException e) {
                        log.warn("Skipping malformed deck key when marking stale: {}", key, e);
                        return Mono.empty();
                    }
                })
                .flatMap(viewerId -> markAsStale(viewerId, profileId))
                .map(added -> added > 0 ? 1L : 0L)
                .reduce(0L, Long::sum)
                .doOnNext(count -> log.info("Marked profile {} as stale in {} decks", profileId, count));
    }

    /**
     * Check if a profile is marked as stale in viewer's deck
     *
     * @param viewerId The viewer ID
     * @param profileId The profile ID to check
     * @return Mono<Boolean> true if profile is stale
     */
    public Mono<Boolean> isStale(UUID viewerId, UUID profileId) {
        return redis.opsForSet()
                .isMember(staleKey(viewerId), profileId.toString());
    }

    /**
     * Get all stale profiles for a viewer
     *
     * @param viewerId The viewer ID
     * @return Flux of stale profile IDs
     */
    public Flux<UUID> getStaleProfiles(UUID viewerId) {
        return redis.opsForSet()
                .members(staleKey(viewerId))
                .map(UUID::fromString);
    }

    /**
     * Remove profile from stale set (e.g., after deck rebuild)
     *
     * @param viewerId The viewer ID
     * @param profileId The profile ID to unmark
     * @return Mono<Long> number of removed items
     */
    public Mono<Long> removeStale(UUID viewerId, UUID profileId) {
        return redis.opsForSet()
                .remove(staleKey(viewerId), profileId.toString());
    }

    /**
     * Clear all stale markers for a viewer (e.g., after complete rebuild)
     *
     * @param viewerId The viewer ID
     * @return Mono<Boolean> true if stale set was deleted
     */
    public Mono<Boolean> clearStale(UUID viewerId) {
        return redis.delete(staleKey(viewerId))
                .map(count -> count > 0);
    }

    // ==================== Phase 2: Distributed Locking ====================

    /**
     * Acquire distributed lock for deck rebuild
     * Uses Redis SET NX (set if not exists) pattern
     *
     * @param viewerId The viewer ID to lock
     * @return Mono<Boolean> true if lock acquired, false if already locked
     */
    public Mono<Boolean> acquireLock(UUID viewerId) {
        return acquireLock(viewerId, DEFAULT_LOCK_TTL);
    }

    /**
     * Acquire distributed lock with custom TTL
     *
     * @param viewerId The viewer ID to lock
     * @param ttl Lock expiration time (auto-release if process dies)
     * @return Mono<Boolean> true if lock acquired
     */
    public Mono<Boolean> acquireLock(UUID viewerId, Duration ttl) {
        String key = lockKey(viewerId);
        log.debug("Attempting to acquire lock for viewer {}", viewerId);

        return redis.opsForValue()
                .setIfAbsent(key, LOCK_VALUE, ttl)
                .doOnNext(acquired -> {
                    if (acquired) {
                        log.debug("Lock acquired for viewer {}", viewerId);
                    } else {
                        log.debug("Lock already held for viewer {}", viewerId);
                    }
                });
    }

    /**
     * Release distributed lock
     *
     * @param viewerId The viewer ID to unlock
     * @return Mono<Boolean> true if lock was released
     */
    public Mono<Boolean> releaseLock(UUID viewerId) {
        String key = lockKey(viewerId);
        log.debug("Releasing lock for viewer {}", viewerId);

        return redis.delete(key)
                .map(count -> count > 0)
                .doOnNext(released -> {
                    if (released) {
                        log.debug("Lock released for viewer {}", viewerId);
                    } else {
                        log.warn("No lock found to release for viewer {}", viewerId);
                    }
                });
    }

    /**
     * Check if lock is currently held for a viewer
     *
     * @param viewerId The viewer ID
     * @return Mono<Boolean> true if lock exists
     */
    public Mono<Boolean> isLocked(UUID viewerId) {
        return redis.hasKey(lockKey(viewerId));
    }

    /**
     * Execute an operation with lock protection
     * Acquires lock, executes operation, releases lock (even on error)
     *
     * @param viewerId The viewer ID to lock
     * @param operation The operation to execute under lock
     * @param <T> Return type of operation
     * @return Mono<T> result of operation, or empty if lock could not be acquired
     */
    public <T> Mono<T> withLock(UUID viewerId, Mono<T> operation) {
        return acquireLock(viewerId)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("Could not acquire lock for viewer {}, skipping operation", viewerId);
                        return Mono.empty();
                    }

                    return operation
                            .doFinally(signal -> releaseLock(viewerId).subscribe());
                });
    }

    // ==================== Phase 2: Filtered Read Methods ====================

    /**
     * Read deck excluding stale profiles
     * Filters out profiles marked as stale
     *
     * @param viewerId The viewer ID
     * @param offset Start offset
     * @param limit Maximum number of profiles to return
     * @return Flux of fresh (non-stale) profile IDs
     */
    public Flux<UUID> readDeckExcludingStale(UUID viewerId, int offset, int limit) {
        // First get stale profiles
        return getStaleProfiles(viewerId)
                .collect(Collectors.toSet())
                .flatMapMany(staleSet -> {
                    if (staleSet.isEmpty()) {
                        // No stale profiles, return normally
                        return readDeck(viewerId, offset, limit);
                    }

                    log.debug("Filtering {} stale profiles for viewer {}", staleSet.size(), viewerId);

                    // Read more profiles to compensate for filtered stale ones
                    // Fetch up to 2x limit to account for stale profiles
                    int fetchLimit = Math.min(limit * 2, 200);

                    return readDeck(viewerId, offset, fetchLimit)
                            .filter(profileId -> !staleSet.contains(profileId))
                            .take(limit);
                });
    }

    /**
     * Read top N profiles excluding stale
     *
     * @param viewerId The viewer ID
     * @param topN Number of fresh profiles to return
     * @return Mono<List<UUID>> list of fresh profile IDs
     */
    public Mono<List<UUID>> readTopExcludingStale(UUID viewerId, int topN) {
        return readDeckExcludingStale(viewerId, 0, topN).collectList();
    }

    /**
     * Remove a specific profile from deck (e.g., after swipe)
     *
     * @param viewerId The viewer ID
     * @param profileId The profile ID to remove
     * @return Mono<Long> number of removed items (0 or 1)
     */
    public Mono<Long> removeFromDeck(UUID viewerId, UUID profileId) {
        String key = deckKey(viewerId);
        log.debug("Removing profile {} from deck of viewer {}", profileId, viewerId);

        return redis.opsForZSet()
                .remove(key, profileId.toString());
    }

    /**
     * Remove a profile from all cached decks.
     * Used when a profile is deleted and must disappear from every viewer deck.
     *
     * @param profileId The deleted profile ID
     * @return Mono<Long> number of decks affected
     */
    public Mono<Long> removeFromAllDecks(UUID profileId) {
        String deletedProfile = profileId.toString();

        return redis.keys("deck:*")
                .filter(key -> DECK_KEY_PATTERN.matcher(key).matches())
                .flatMap(key -> redis.opsForZSet().remove(key, deletedProfile))
                .map(removed -> removed > 0 ? 1L : 0L)
                .reduce(0L, Long::sum)
                .doOnNext(count -> log.info("Removed deleted profile {} from {} decks", profileId, count));
    }

    /**
     * Remove multiple profiles from deck in batch
     *
     * @param viewerId The viewer ID
     * @param profileIds Set of profile IDs to remove
     * @return Mono<Long> total number of removed items
     */
    public Mono<Long> removeMultipleFromDeck(UUID viewerId, Set<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Mono.just(0L);
        }

        String key = deckKey(viewerId);
        log.debug("Removing {} profiles from deck of viewer {}", profileIds.size(), viewerId);

        String[] profileStrings = profileIds.stream()
                .map(UUID::toString)
                .toArray(String[]::new);

        return redis.opsForZSet()
                .remove(key, (Object[]) profileStrings);
    }

    /**
     * Check if deck exists and is not empty
     *
     * @param viewerId The viewer ID
     * @return Mono<Boolean> true if deck exists and has profiles
     */
    public Mono<Boolean> exists(UUID viewerId) {
        return size(viewerId)
                .map(size -> size > 0)
                .defaultIfEmpty(false);
    }

    // ==================== Preferences Cache (Phase 2.5) ====================

    /**
     * Check if preferences result is cached
     *
     * @param minAge Min age preference
     * @param maxAge Max age preference
     * @param gender Gender preference
     * @return Mono<Boolean> true if cached
     */
    public Mono<Boolean> hasPreferencesCache(int minAge, int maxAge, String gender) {
        return redis.hasKey(preferencesKey(minAge, maxAge, gender));
    }

    /**
     * Get cached candidate IDs for specific preferences
     *
     * @param minAge Min age preference
     * @param maxAge Max age preference
     * @param gender Gender preference
     * @return Flux of candidate profile IDs
     */
    public Flux<UUID> getCandidatesByPreferences(int minAge, int maxAge, String gender) {
        String key = preferencesKey(minAge, maxAge, gender);
        log.debug("Fetching preferences cache: {}", key);

        return redis.opsForSet()
                .members(key)
                .map(UUID::fromString)
                .doOnComplete(() -> log.debug("Preferences cache HIT: {}", key));
    }

    /**
     * Cache candidate IDs for specific preferences
     *
     * @param minAge Min age preference
     * @param maxAge Max age preference
     * @param gender Gender preference
     * @param candidateIds List of candidate profile IDs
     * @return Mono<Long> number of items added
     */
    public Mono<Long> cachePreferencesResult(int minAge, int maxAge, String gender, List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            log.debug("No candidates to cache for preferences {}/{}/{}", minAge, maxAge, gender);
            return Mono.just(0L);
        }

        String key = preferencesKey(minAge, maxAge, gender);
        log.debug("Caching {} candidates for preferences: {}", candidateIds.size(), key);

        String[] candidateStrings = candidateIds.stream()
                .map(UUID::toString)
                .toArray(String[]::new);

        Duration ttl = Duration.ofMinutes(preferencesCacheTtlMinutes);

        return redis.opsForSet()
                .add(key, candidateStrings)
                .flatMap(count -> redis.expire(key, ttl).thenReturn(count))
                .doOnSuccess(count -> log.info("Cached {} candidates for preferences {} (TTL: {})",
                        count, key, ttl));
    }

    /**
     * Invalidate preferences cache
     * Called when profile with these preferences is updated
     *
     * @param minAge Min age preference
     * @param maxAge Max age preference
     * @param gender Gender preference
     * @return Mono<Boolean> true if cache was deleted
     */
    public Mono<Boolean> invalidatePreferencesCache(int minAge, int maxAge, String gender) {
        String key = preferencesKey(minAge, maxAge, gender);
        log.info("Invalidating preferences cache: {}", key);

        return redis.delete(key)
                .map(count -> count > 0)
                .doOnNext(deleted -> {
                    if (deleted) {
                        log.debug("Preferences cache invalidated: {}", key);
                    } else {
                        log.debug("Preferences cache not found (already expired?): {}", key);
                    }
                });
    }

    /**
     * Get size of preferences cache
     *
     * @param minAge Min age preference
     * @param maxAge Max age preference
     * @param gender Gender preference
     * @return Mono<Long> number of candidates in cache
     */
    public Mono<Long> getPreferencesCacheSize(int minAge, int maxAge, String gender) {
        return redis.opsForSet()
                .size(preferencesKey(minAge, maxAge, gender));
    }
}
