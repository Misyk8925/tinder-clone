package com.tinder.deck.service.pipeline.util;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Helper component for preferences cache operations
 * Handles cache hit/miss logic and background caching
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PreferencesCacheHelper {

    private final DeckCache deckCache;
    private final ProfilesHttp profilesHttp;

    /**
     * Fetch candidates using preferences cache
     * On CACHE HIT: fetch profiles by IDs
     * On CACHE MISS: query database and cache result
     *
     * @param viewerId   Viewer's ID
     * @param prefs      Search preferences
     * @param timeoutMs  Request timeout
     * @param retries    Number of retries
     * @param searchLimit Maximum number of results
     * @param databaseQueryCallback Callback to execute database query on cache miss
     * @return Flux of candidate profile IDs
     */
    public Flux<UUID> fetchCandidateIds(
            UUID viewerId,
            SharedPreferencesDto prefs,
            long timeoutMs,
            int retries,
            int searchLimit,
            DatabaseQueryCallback databaseQueryCallback) {

        log.debug("Checking preferences cache for {}/{}/{}",
                prefs.minAge(), prefs.maxAge(), prefs.gender());

        return deckCache.hasPreferencesCache(prefs.minAge(), prefs.maxAge(), prefs.gender())
                .flatMapMany(cacheExists -> {
                    if (cacheExists) {
                        return handleCacheHit(prefs);
                    } else {
                        return handleCacheMiss(viewerId, prefs, searchLimit, databaseQueryCallback);
                    }
                });
    }

    /**
     * Fetch full profiles by cached IDs
     *
     * @param cachedIds List of cached profile IDs
     * @param timeoutMs Request timeout
     * @param retries   Number of retries
     * @param fallbackCallback Callback to execute on error
     * @return Flux of full profiles
     */
    public Flux<SharedProfileDto> fetchProfilesByIds(
            List<UUID> cachedIds,
            long timeoutMs,
            int retries,
            Flux<SharedProfileDto> fallbackCallback) {

        if (cachedIds.isEmpty()) {
            log.warn("Preferences cache returned empty list, falling back to DB");
            return fallbackCallback;
        }

        log.debug("Fetching {} profiles from cache IDs", cachedIds.size());

        return profilesHttp.getProfilesByIds(cachedIds)
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch profiles by IDs, falling back to DB: {}", e.toString());
                    return fallbackCallback;
                });
    }

    /**
     * Cache preference results in background
     *
     * @param prefs       Search preferences
     * @param candidateIds List of candidate IDs to cache
     */
    public void cacheInBackground(SharedPreferencesDto prefs, List<UUID> candidateIds) {
        log.debug("Caching {} candidate IDs for preferences {}/{}/{}",
                candidateIds.size(), prefs.minAge(), prefs.maxAge(), prefs.gender());

        deckCache.cachePreferencesResult(
                prefs.minAge(), prefs.maxAge(), prefs.gender(),
                candidateIds)
                .subscribe(
                        count -> log.debug("Cached {} candidates for preferences cache", count),
                        error -> log.error("Failed to cache preferences result", error)
                );
    }

    private Flux<UUID> handleCacheHit(SharedPreferencesDto prefs) {
        log.info("Preferences cache HIT for {}/{}/{}",
                prefs.minAge(), prefs.maxAge(), prefs.gender());

        return deckCache.getCandidatesByPreferences(
                prefs.minAge(), prefs.maxAge(), prefs.gender());
    }

    private Flux<UUID> handleCacheMiss(
            UUID viewerId,
            SharedPreferencesDto prefs,
            int searchLimit,
            DatabaseQueryCallback callback) {

        log.info("Preferences cache MISS for {}/{}/{}, querying DB",
                prefs.minAge(), prefs.maxAge(), prefs.gender());

        return callback.queryDatabase(viewerId, prefs, searchLimit)
                .collectList()
                .flatMapMany(candidates -> {
                    if (candidates.isEmpty()) {
                        log.debug("No candidates found in DB for {}/{}/{}",
                                prefs.minAge(), prefs.maxAge(), prefs.gender());
                        return Flux.fromIterable(candidates);
                    }

                    List<UUID> candidateIds = candidates.stream()
                            .map(SharedProfileDto::id)
                            .toList();

                    cacheInBackground(prefs, candidateIds);

                    return Flux.fromIterable(candidates);
                })
                .map(SharedProfileDto::id);
    }

    @FunctionalInterface
    public interface DatabaseQueryCallback {
        Flux<SharedProfileDto> queryDatabase(UUID viewerId, SharedPreferencesDto prefs, int searchLimit);
    }
}
