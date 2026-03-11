package com.tinder.profiles.preferences;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
@Slf4j
public class PreferencesService {

    private final PreferencesRepository repo;

    /**
     * In-memory L1 cache keyed by "minAge:maxAge:gender:maxRange".
     * Avoids a DB roundtrip for repeated combinations within the same JVM process.
     */
    private final ConcurrentHashMap<String, Preferences> preferencesCache = new ConcurrentHashMap<>();

    /**
     * Find or atomically create a Preferences row.
     *
     * <p>Strategy: issue a single {@code INSERT ... ON CONFLICT DO NOTHING} upsert, then
     * read back the (possibly pre-existing) row.  Both operations share the <em>same</em>
     * transaction and therefore the <em>same</em> JDBC connection as the caller — no second
     * connection is ever acquired, so the HikariCP pool cannot be exhausted even at high
     * concurrency.</p>
     *
     * <p>The upsert is idempotent: concurrent threads may all execute it simultaneously —
     * only one INSERT wins, the rest are silently ignored by {@code ON CONFLICT DO NOTHING}.
     * All threads then read back the single committed row.</p>
     */
    @Transactional
    public Preferences findOrCreate(PreferencesDto preferences) {
        Integer minAge  = preferences.getMinAge();
        Integer maxAge  = preferences.getMaxAge();
        String  gender  = preferences.getGender();
        Integer maxRange = preferences.getMaxRange() != null ? preferences.getMaxRange() : 50;

        String cacheKey = minAge + ":" + maxAge + ":" + gender + ":" + maxRange;

        // L1: in-memory cache — fast path (no DB hit at all for warm entries)
        Preferences cached = preferencesCache.get(cacheKey);
        if (cached != null) {
            log.debug("Preferences L1 cache hit for key '{}'", cacheKey);
            return cached;
        }

        // Atomic upsert: INSERT the row if it does not exist yet, otherwise do nothing.
        // ON CONFLICT DO NOTHING means no exception is ever thrown, even under high concurrency.
        repo.upsert(minAge, maxAge, gender, maxRange);

        // Read back the row that either we just inserted or a concurrent thread inserted first.
        Preferences result = repo.findByValues(minAge, maxAge, gender, maxRange)
                .orElseThrow(() -> new IllegalStateException(
                        "Preferences row missing after upsert for key: " + cacheKey));

        log.debug("Preferences resolved: id={}, key='{}'", result.getId(), cacheKey);
        preferencesCache.put(cacheKey, result);
        return result;
    }
}
