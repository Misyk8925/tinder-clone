package com.tinder.profiles.preferences;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class PreferencesService {

    private final PreferencesRepository repo;

    /** In-memory cache keyed by "minAge:maxAge:gender:maxRange" to avoid redundant DB lookups. */
    private final ConcurrentHashMap<String, Preferences> preferencesCache = new ConcurrentHashMap<>();

    @Transactional
    public Preferences findOrCreate(PreferencesDto preferences) {
        Integer minAge = preferences.getMinAge();
        Integer maxAge = preferences.getMaxAge();
        String gender = preferences.getGender();
        Integer maxRange = preferences.getMaxRange() != null ? preferences.getMaxRange() : 50;

        String cacheKey = minAge + ":" + maxAge + ":" + gender + ":" + maxRange;

        // L1: In-memory cache check
        Preferences cached = preferencesCache.get(cacheKey);
        if (cached != null) {
            log.debug("Preferences cache hit for key '{}'", cacheKey);
            return cached;
        }

        // L2: Database lookup
        Optional<Preferences> existing = repo.findByValues(minAge, maxAge, gender, maxRange);

        if (existing.isPresent()) {
            log.debug("Reusing existing preferences: minAge={}, maxAge={}, gender={}, maxRange={}",
                     minAge, maxAge, gender, maxRange);
            preferencesCache.put(cacheKey, existing.get());
            return existing.get();
        }

        // Create new preferences only if none exist
        Preferences newPreferences = Preferences.builder()
                .minAge(minAge)
                .maxAge(maxAge)
                .gender(gender)
                .maxRange(maxRange)
                .build();

        Preferences saved = repo.save(newPreferences);
        log.info("Created new preferences: id={}, minAge={}, maxAge={}, gender={}, maxRange={}",
                saved.getId(), minAge, maxAge, gender, maxRange);
        preferencesCache.put(cacheKey, saved);
        return saved;
    }
}
