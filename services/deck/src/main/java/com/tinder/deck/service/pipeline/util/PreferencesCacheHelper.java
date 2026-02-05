package com.tinder.deck.service.pipeline.util;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.DeckCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
     * Fetch full profiles by cached IDs
     *
     * @param cachedIds List of cached profile IDs
     * @param fallbackCallback Callback to execute on error
     * @return Flux of full profiles
     */
    public Flux<SharedProfileDto> fetchProfilesByIds(
            List<UUID> cachedIds,
            Flux<SharedProfileDto> fallbackCallback) {

        if (cachedIds.isEmpty()) {
            log.warn("Preferences cache returned empty list, falling back to DB");
            return fallbackCallback;
        }

        log.debug("Fetching {} profiles from cache IDs", cachedIds.size());

        return profilesHttp.getProfilesByIds(cachedIds)
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

}
