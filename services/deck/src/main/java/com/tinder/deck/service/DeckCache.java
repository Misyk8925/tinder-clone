package com.tinder.deck.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.deck.DeckRedisKeys;
import com.tinder.contracts.dto.DeckEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckCache {

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // Reverse index: profileId -> set of viewerIds whose deck currently contains the profile.
    // Maintained by writeDeck() and consumed by removeFromAllDecks() so fan-out on profile
    // delete/critical-change scales with the number of AFFECTED decks, not the total user count
    // (i.e. avoids a full "KEYS deck:*" keyspace scan). Over-inclusion is tolerated (a stale
    // viewer entry just makes the corresponding ZREM a no-op); TTL bounds growth.
    // Lock configuration
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(30);
    private static final String LOCK_VALUE = "locked";

    // Stale tracking configuration
    private static final Duration DEFAULT_STALE_TTL = Duration.ofHours(24);

    // Preferences cache configuration
    @Value("${deck.preferences-cache-ttl-minutes:5}")
    private long preferencesCacheTtlMinutes;

    @Value("${deck.rebuild.stale.ttl-hours:24}")
    private long invalidationTtlHours;


    public Mono<Void> writeDeck(UUID viewerId, List<Entry<UUID, Double>> deck, Duration ttl) {
        String key   = DeckRedisKeys.deck(viewerId);
        String tsKey = DeckRedisKeys.buildTimestamp(viewerId);

        ReactiveZSetOperations<String, String> z = redis.opsForZSet();

        Mono<Long> addAll = Flux.fromIterable(deck)
                .map(e -> ZSetOperations.TypedTuple.of(serializeEntry(DeckEntry.fresh(e.getKey())), e.getValue()))
                .collect(Collectors.toSet())
                .flatMap(tuples -> z.addAll(key, tuples));

        // Maintain the reverse index so deletions/critical-changes can fan out cheaply.
        // For each profile in this deck, record that viewerId's deck contains it.
        Mono<Void> indexAll = Flux.fromIterable(deck)
                .flatMap(e -> {
                    String containsKey = DeckRedisKeys.contains(e.getKey());
                    return redis.opsForSet().add(containsKey, viewerId.toString())
                            .then(redis.expire(containsKey, ttl));
                })
                .then();

        return redis.delete(key, tsKey)
                .then(addAll)
                .then(redis.expire(key, ttl))
                .then(redis.opsForValue().set(tsKey, String.valueOf(System.currentTimeMillis()), ttl))
                .then(indexAll)
                .then();
    }

    public Flux<UUID> readDeck(UUID viewerId, int offset, int limit) {
        String key = DeckRedisKeys.deck(viewerId);
        long end = offset + Math.max(limit, 1) - 1;
        return redis.opsForZSet()
                .reverseRange(key, org.springframework.data.domain.Range.closed((long)offset, end))
                .map(member -> deserializeEntry(member).profileId());
    }

    public Mono<Long> size(UUID viewerId) {
        return redis.opsForZSet().size(DeckRedisKeys.deck(viewerId));
    }

    public Mono<Optional<Instant>> getBuildInstant(UUID viewerId) {
        return redis.opsForValue().get(DeckRedisKeys.buildTimestamp(viewerId))
                .map(v -> Optional.of(Instant.ofEpochMilli(Long.parseLong(v))))
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Long> invalidate(UUID viewerId) {
        return redis.delete(DeckRedisKeys.deck(viewerId), DeckRedisKeys.buildTimestamp(viewerId));
    }

    public Mono<Boolean> markProfileInvalidated(UUID profileId) {
        Duration ttl = Duration.ofHours(invalidationTtlHours);
        return redis.opsForValue()
                .set(DeckRedisKeys.invalidatedAt(profileId), String.valueOf(System.currentTimeMillis()), ttl);
    }

    public Mono<Boolean> markProfileDeleted(UUID profileId) {
        Duration ttl = Duration.ofHours(invalidationTtlHours);
        return redis.opsForSet()
                .add(DeckRedisKeys.DELETED_PROFILES, profileId.toString())
                .flatMap(count -> redis.expire(DeckRedisKeys.DELETED_PROFILES, ttl).thenReturn(count > 0));
    }

    public Mono<Void> touchRecentViewer(UUID viewerId) {
        double now = System.currentTimeMillis();
        return redis.opsForZSet()
                .add(DeckRedisKeys.RECENT_VIEWERS, viewerId.toString(), now)
                .then();
    }

    public Flux<UUID> getRecentViewerIds(Duration window, int limit) {
        double cutoff = System.currentTimeMillis() - window.toMillis();
        return redis.opsForZSet()
                .reverseRangeByScore(DeckRedisKeys.RECENT_VIEWERS,
                        org.springframework.data.domain.Range.closed(cutoff, Double.MAX_VALUE))
                .take(limit)
                .map(UUID::fromString);
    }

    public Mono<List<UUID>> readTop(UUID viewerId, int topN) {
        return readDeck(viewerId, 0, topN).collectList();
    }

    public Flux<Entry<UUID, Double>> readRangeWithScores(UUID viewerId, long start, long end) {
        return redis.opsForZSet()
                .reverseRangeWithScores(DeckRedisKeys.deck(viewerId), org.springframework.data.domain.Range.closed(start, end))
                .map(t -> Map.entry(deserializeEntry(Objects.requireNonNull(t.getValue())).profileId(),
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
        String key = DeckRedisKeys.stale(viewerId);
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
        return redis.keys(DeckRedisKeys.PRIMARY_DECK_SCAN_PATTERN)
                .filter(key -> DeckRedisKeys.PRIMARY_DECK_KEY.matcher(key).matches())
                .flatMap(key -> {
                    String idPart = key.substring(DeckRedisKeys.PRIMARY_DECK_PREFIX.length());
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
                .isMember(DeckRedisKeys.stale(viewerId), profileId.toString());
    }

    /**
     * Get all stale profiles for a viewer
     *
     * @param viewerId The viewer ID
     * @return Flux of stale profile IDs
     */
    public Flux<UUID> getStaleProfiles(UUID viewerId) {
        return redis.opsForSet()
                .members(DeckRedisKeys.stale(viewerId))
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
                .remove(DeckRedisKeys.stale(viewerId), profileId.toString());
    }

    /**
     * Clear all stale markers for a viewer (e.g., after complete rebuild)
     *
     * @param viewerId The viewer ID
     * @return Mono<Boolean> true if stale set was deleted
     */
    public Mono<Boolean> clearStale(UUID viewerId) {
        return redis.delete(DeckRedisKeys.stale(viewerId))
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
        String key = DeckRedisKeys.lock(viewerId);
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
        String key = DeckRedisKeys.lock(viewerId);
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
        return redis.hasKey(DeckRedisKeys.lock(viewerId));
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
        String key = DeckRedisKeys.deck(viewerId);
        log.debug("Removing profile {} from deck of viewer {}", profileId, viewerId);

        return findMemberByProfileId(key, profileId)
                .flatMap(member -> redis.opsForZSet().remove(key, member))
                .defaultIfEmpty(0L);
    }

    /**
     * Marks a profile as swiped in the viewer's deck without removing it.
     * Used when a swipe-saved event arrives before the scheduler rebuilds the deck.
     * The profiles service reads isSwiped=true and excludes this profile from results.
     */
    public Mono<Void> markAsSwiped(UUID swiperId, UUID swipedId) {
        String key = DeckRedisKeys.deck(swiperId);
        log.debug("Marking profile {} as swiped in deck of viewer {}", swipedId, swiperId);

        return redis.opsForZSet()
                .scan(key, ScanOptions.scanOptions().match("*" + swipedId + "*").build())
                .next()
                .flatMap(tuple -> {
                    String oldMember = tuple.getValue();
                    Double score = tuple.getScore();
                    if (oldMember == null || score == null) {
                        return Mono.empty();
                    }
                    DeckEntry updated = deserializeEntry(oldMember).withSwiped();
                    return redis.opsForZSet().remove(key, oldMember)
                            .then(redis.opsForZSet().add(key, serializeEntry(updated), score));
                })
                .then();
    }

    /**
     * Remove a profile from all cached decks that contain it.
     * Used when a profile is deleted or its critical fields change and it must disappear from
     * every viewer's deck before the next rebuild.
     *
     * <p>Uses the {@code deck:contains:{profileId}} reverse index to touch only the AFFECTED
     * decks — no {@code KEYS deck:*} keyspace scan. The reverse index is then deleted; rebuilds
     * repopulate it. Reverse-index entries may be stale (over-inclusive), in which case the
     * per-deck {@code findMemberByProfileId} simply finds nothing and the ZREM is a no-op.
     *
     * @param profileId The profile to purge from all decks
     * @return Mono<Long> number of decks actually affected
     */
    public Mono<Long> removeFromAllDecks(UUID profileId) {
        String containsKey = DeckRedisKeys.contains(profileId);
        return redis.opsForSet().members(containsKey)
                .flatMap(viewerIdStr -> {
                    UUID viewerId;
                    try {
                        viewerId = UUID.fromString(viewerIdStr);
                    } catch (IllegalArgumentException e) {
                        log.warn("Skipping malformed viewerId {} in reverse index for profile {}", viewerIdStr, profileId);
                        return Mono.just(0L);
                    }
                    String dKey = DeckRedisKeys.deck(viewerId);
                    return findMemberByProfileId(dKey, profileId)
                            .flatMap(member -> redis.opsForZSet().remove(dKey, member))
                            .defaultIfEmpty(0L)
                            .map(removed -> removed > 0 ? 1L : 0L);
                })
                .reduce(0L, Long::sum)
                .flatMap(count -> redis.delete(containsKey).thenReturn(count))
                .doOnNext(count -> log.info("Removed profile {} from {} decks (via reverse index)", profileId, count));
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

        String key = DeckRedisKeys.deck(viewerId);
        log.debug("Removing {} profiles from deck of viewer {}", profileIds.size(), viewerId);

        return Flux.fromIterable(profileIds)
                .flatMap(profileId -> findMemberByProfileId(key, profileId))
                .collect(Collectors.toSet())
                .flatMap(members -> {
                    if (members.isEmpty()) return Mono.just(0L);
                    return redis.opsForZSet().remove(key, members.toArray(new Object[0]));
                });
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
        return redis.hasKey(DeckRedisKeys.preferences(minAge, maxAge, gender));
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
        String key = DeckRedisKeys.preferences(minAge, maxAge, gender);
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

        String key = DeckRedisKeys.preferences(minAge, maxAge, gender);
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
        String key = DeckRedisKeys.preferences(minAge, maxAge, gender);
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
                .size(DeckRedisKeys.preferences(minAge, maxAge, gender));
    }

    // ==================== Serialization Helpers ====================

    private String serializeEntry(DeckEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize DeckEntry for profileId=" + entry.profileId(), e);
        }
    }

    private DeckEntry deserializeEntry(String json) {
        try {
            return objectMapper.readValue(json, DeckEntry.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize DeckEntry from: " + json, e);
        }
    }

    private Mono<String> findMemberByProfileId(String deckKey, UUID profileId) {
        return redis.opsForZSet()
                .scan(deckKey, ScanOptions.scanOptions().match("*" + profileId + "*").build())
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .next();
    }
}
