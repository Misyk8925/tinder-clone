package com.tinder.deck.service.pipeline;


import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.pipeline.util.LocationFilterUtil;
import com.tinder.deck.service.pipeline.util.PreferencesCacheHelper;
import com.tinder.deck.service.pipeline.util.PreferencesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CandidateSearchStage {

    private final ProfilesHttp profilesHttp;
    private final DeckCache deckCache;
    private final PreferencesCacheHelper preferencesCacheHelper;

    @Value("${deck.search-limit:2000}")
    private int searchLimit;

    @Value("${deck.request-timeout-ms:5000}")
    private long timeoutMs;

    @Value("${deck.retries:3}")
    private int retries;

    @Value("${deck.preferences-cache-enabled:false}")
    private boolean preferencesCacheEnabled;


    public Flux<SharedProfileDto> searchCandidates(SharedProfileDto viewer) {
        SharedPreferencesDto prefs = PreferencesUtil.getPreferencesOrDefault(viewer);

        log.debug("Searching candidates for viewer {} with preferences: {} (batch rebuild)",
                viewer.id(), prefs);

        // For batch rebuilds: use preferences cache if enabled
        if (preferencesCacheEnabled) {
            log.debug("Preferences cache ENABLED, using cache for batch rebuild");
            return searchWithPreferencesCache(viewer, prefs);
        }

        // Fallback: direct DB query with location
        log.debug("Preferences cache DISABLED, using direct DB query WITH location");
        return searchFromDatabase(viewer.id(), prefs);
    }


    private Flux<SharedProfileDto> searchWithPreferencesCache(
            SharedProfileDto viewer,
            SharedPreferencesDto prefs) {

        return deckCache.hasPreferencesCache(prefs.minAge(), prefs.maxAge(), prefs.gender())
                .flatMapMany(cacheExists -> {
                    if (cacheExists) {
                        return handleCacheHit(viewer, prefs);
                    } else {
                        return handleCacheMiss(viewer, prefs);
                    }
                });
    }

    /**
     * Handle cache HIT: fetch profiles by IDs and filter by location
     */
    private Flux<SharedProfileDto> handleCacheHit(SharedProfileDto viewer, SharedPreferencesDto prefs) {
        log.info("Preferences cache HIT for {}/{}/{}",
                prefs.minAge(), prefs.maxAge(), prefs.gender());

        return deckCache.getCandidatesByPreferences(prefs.minAge(), prefs.maxAge(), prefs.gender())
                .collectList()
                .flatMapMany(cachedIds ->
                        preferencesCacheHelper.fetchProfilesByIds(
                                cachedIds,
                                timeoutMs,
                                retries,
                                searchFromDatabaseAndCache(viewer.id(), prefs)
                        )
                )
                .filter(candidate -> LocationFilterUtil.isWithinRange(viewer, candidate, prefs.maxRange()));
    }

    /**
     * Handle cache MISS: query DB, cache result, filter by location
     */
    private Flux<SharedProfileDto> handleCacheMiss(SharedProfileDto viewer, SharedPreferencesDto prefs) {
        log.info("Preferences cache MISS for {}/{}/{}, querying DB",
                prefs.minAge(), prefs.maxAge(), prefs.gender());

        return searchFromDatabaseAndCache(viewer.id(), prefs);
    }

    /**
     * Search from database and cache the result
     */
    private Flux<SharedProfileDto> searchFromDatabaseAndCache(UUID viewerId, SharedPreferencesDto prefs) {
        return searchFromDatabase(viewerId, prefs)
                .collectList()
                .flatMapMany(candidates -> {
                    if (!candidates.isEmpty()) {
                        preferencesCacheHelper.cacheInBackground(
                                prefs,
                                candidates.stream().map(SharedProfileDto::id).toList()
                        );
                    }
                    return Flux.fromIterable(candidates);
                });
    }

    /**
     * Direct database query (original behavior)
     */
    private Flux<SharedProfileDto> searchFromDatabase(UUID viewerId, SharedPreferencesDto prefs) {
        return profilesHttp.searchProfiles(viewerId, prefs, searchLimit)
                .timeout(Duration.ofMillis(timeoutMs))
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Candidate search failed for viewer {}: {}",
                            viewerId, e.getMessage());
                    return Flux.empty();
                })
                .doOnComplete(() -> log.debug("Candidate search completed for viewer {}", viewerId));
    }

}
