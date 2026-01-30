package com.tinder.profiles.util;

import com.tinder.profiles.preferences.Preferences;
import com.tinder.profiles.profile.Profile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Test helper for managing deck cache in Redis during integration tests.
 * Maintains a map of correct decks for each user based on preferences matching.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DeckCacheTestHelper {

    private final StringRedisTemplate redis;

    private static final String DECK_KEY_PREFIX = "deck:";
    private static final String DECK_TS_KEY_PREFIX = "deck_ts:";
    private static final long DEFAULT_TTL_MINUTES = 60;

    /**
     * Map storing correct deck candidates for each user based on preferences.
     * Key: viewerId, Value: List of candidate IDs with scores (sorted by score descending)
     */
    private final Map<UUID, List<Map.Entry<UUID, Double>>> correctDecksMap = new HashMap<>();

    /**
     * Calculate correct decks for all profiles based on preferences matching.
     * This creates a deterministic map of who should see whom.
     *
     * @param profiles all profiles to calculate decks for
     */
    public void calculateCorrectDecks(List<Profile> profiles) {
        log.info("Calculating correct decks for {} profiles", profiles.size());
        correctDecksMap.clear();

        for (Profile viewer : profiles) {
            List<Map.Entry<UUID, Double>> candidates = calculateDeckForViewer(viewer, profiles);
            correctDecksMap.put(viewer.getProfileId(), candidates);

            log.debug("Calculated deck for {} ({}): {} candidates",
                viewer.getName(),
                viewer.getGender(),
                candidates.size());
        }

        log.info("Calculated decks for {} profiles, total candidates mapped: {}",
            profiles.size(),
            correctDecksMap.values().stream().mapToInt(List::size).sum());
    }

    /**
     * Calculate deck for a single viewer based on preferences.
     *
     * @param viewer the viewer profile
     * @param allProfiles all available profiles
     * @return list of candidate IDs with scores, sorted by score descending
     */
    private List<Map.Entry<UUID, Double>> calculateDeckForViewer(Profile viewer, List<Profile> allProfiles) {
        Preferences prefs = viewer.getPreferences();

        return allProfiles.stream()
            // Filter out viewer themselves
            .filter(p -> !p.getProfileId().equals(viewer.getProfileId()))
            // Filter out deleted profiles
            .filter(p -> !p.isDeleted())
            // Filter by gender preference
            .filter(p -> matchesGenderPreference(p.getGender(), prefs.getGender()))
            // Filter by age preference
            .filter(p -> matchesAgePreference(p.getAge(), prefs.getMinAge(), prefs.getMaxAge()))
            // Map to entries with deterministic scores
            .map(p -> {
                // Deterministic score calculation:
                // 1. Age proximity score (closer age = higher score)
                double ageScore = 100.0 - Math.abs(p.getAge() - viewer.getAge());

                // 2. Profile ID ordering score (for deterministic ordering)
                double idScore = p.getProfileId().compareTo(viewer.getProfileId()) > 0 ? 10.0 : 5.0;

                double totalScore = ageScore + idScore;
                return Map.entry(p.getProfileId(), totalScore);
            })
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue())) // Sort by score descending
            .collect(Collectors.toList());
    }

    /**
     * Check if candidate gender matches viewer's preference
     */
    private boolean matchesGenderPreference(String candidateGender, String preferredGender) {
        if (preferredGender == null ||
            preferredGender.equalsIgnoreCase("any") ||
            preferredGender.equalsIgnoreCase("all")) {
            return true;
        }
        return candidateGender.equalsIgnoreCase(preferredGender);
    }

    /**
     * Check if candidate age matches viewer's preference
     */
    private boolean matchesAgePreference(Integer candidateAge, Integer minAge, Integer maxAge) {
        if (minAge != null && candidateAge < minAge) {
            return false;
        }
        if (maxAge != null && candidateAge > maxAge) {
            return false;
        }
        return true;
    }

    /**
     * Get the correct deck for a viewer from the calculated map.
     *
     * @param viewerId viewer's profile ID
     * @return list of candidate IDs with scores, or empty list if not found
     */
    public List<Map.Entry<UUID, Double>> getCorrectDeck(UUID viewerId) {
        return correctDecksMap.getOrDefault(viewerId, Collections.emptyList());
    }

    /**
     * Write correct deck for a viewer to Redis cache.
     *
     * @param viewerId viewer's profile ID
     */
    public void writeDeckToRedis(UUID viewerId) {
        List<Map.Entry<UUID, Double>> deck = correctDecksMap.get(viewerId);
        if (deck == null || deck.isEmpty()) {
            log.warn("No deck found for viewer {} in correctDecksMap", viewerId);
            return;
        }

        writeDeck(viewerId, deck);
    }

    /**
     * Write all calculated decks to Redis cache.
     */
    public void writeAllDecksToRedis() {
        log.info("Writing {} decks to Redis", correctDecksMap.size());
        int written = 0;

        for (Map.Entry<UUID, List<Map.Entry<UUID, Double>>> entry : correctDecksMap.entrySet()) {
            UUID viewerId = entry.getKey();
            List<Map.Entry<UUID, Double>> deck = entry.getValue();

            if (!deck.isEmpty()) {
                writeDeck(viewerId, deck);
                written++;
            }
        }

        log.info("Successfully wrote {} decks to Redis", written);
    }

    /**
     * Write a deck to Redis cache for a viewer.
     * Each candidate is stored with a score for ordering.
     *
     * @param viewerId   the viewer's profile ID
     * @param candidates list of candidate profile IDs with their scores
     */
    public void writeDeck(UUID viewerId, List<Map.Entry<UUID, Double>> candidates) {
        String key = deckKey(viewerId);

        // Clear existing deck
        redis.delete(key);

        if (candidates == null || candidates.isEmpty()) {
            log.debug("Empty deck provided for viewer {}", viewerId);
            return;
        }

        // Add all candidates to sorted set
        for (Map.Entry<UUID, Double> entry : candidates) {
            redis.opsForZSet().add(key, entry.getKey().toString(), entry.getValue());
        }

        // Set TTL
        redis.expire(key, DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);

        // Store build timestamp
        String tsKey = deckTsKey(viewerId);
        redis.opsForValue().set(tsKey, String.valueOf(System.currentTimeMillis()));
        redis.expire(tsKey, DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);

        log.debug("Wrote deck for viewer {} with {} candidates", viewerId, candidates.size());
    }

    /**
     * Check if a deck exists for a viewer
     */
    public boolean exists(UUID viewerId) {
        String key = deckKey(viewerId);
        Boolean hasKey = redis.hasKey(key);
        if (Boolean.TRUE.equals(hasKey)) {
            Long size = redis.opsForZSet().size(key);
            return size != null && size > 0;
        }
        return false;
    }

    /**
     * Get deck size for a viewer
     */
    public long getDeckSize(UUID viewerId) {
        String key = deckKey(viewerId);
        Long size = redis.opsForZSet().size(key);
        return size != null ? size : 0;
    }

    /**
     * Read deck contents for a viewer
     */
    public Set<String> readDeck(UUID viewerId, long start, long end) {
        String key = deckKey(viewerId);
        return redis.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * Verify that the deck in Redis matches the calculated correct deck
     *
     * @param viewerId viewer's profile ID
     * @return true if Redis deck matches calculated deck, false otherwise
     */
    public boolean verifyDeckInRedis(UUID viewerId) {
        List<Map.Entry<UUID, Double>> correctDeck = correctDecksMap.get(viewerId);
        if (correctDeck == null) {
            log.warn("No correct deck found for viewer {}", viewerId);
            return false;
        }

        String key = deckKey(viewerId);
        Long redisSize = redis.opsForZSet().size(key);

        if (redisSize == null || redisSize != correctDeck.size()) {
            log.warn("Deck size mismatch for viewer {}: expected {}, got {}",
                viewerId, correctDeck.size(), redisSize);
            return false;
        }

        // Verify all candidates are present with correct scores
        Set<String> redisContents = redis.opsForZSet().reverseRange(key, 0, -1);
        if (redisContents == null || redisContents.size() != correctDeck.size()) {
            return false;
        }

        Set<String> expectedIds = correctDeck.stream()
            .map(e -> e.getKey().toString())
            .collect(Collectors.toSet());

        return redisContents.equals(expectedIds);
    }

    /**
     * Clear all deck caches
     */
    public void clearAllDecks() {
        Set<String> deckKeys = redis.keys(DECK_KEY_PREFIX + "*");
        Set<String> tsKeys = redis.keys(DECK_TS_KEY_PREFIX + "*");

        if (deckKeys != null && !deckKeys.isEmpty()) {
            redis.delete(deckKeys);
        }
        if (tsKeys != null && !tsKeys.isEmpty()) {
            redis.delete(tsKeys);
        }

        log.debug("Cleared all deck caches from Redis");
    }

    /**
     * Clear the in-memory correct decks map
     */
    public void clearCorrectDecksMap() {
        correctDecksMap.clear();
        log.debug("Cleared correct decks map");
    }

    /**
     * Get statistics about calculated decks
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDecks", correctDecksMap.size());
        stats.put("totalCandidates", correctDecksMap.values().stream().mapToInt(List::size).sum());

        if (!correctDecksMap.isEmpty()) {
            int minSize = correctDecksMap.values().stream().mapToInt(List::size).min().orElse(0);
            int maxSize = correctDecksMap.values().stream().mapToInt(List::size).max().orElse(0);
            double avgSize = correctDecksMap.values().stream().mapToInt(List::size).average().orElse(0);

            stats.put("minDeckSize", minSize);
            stats.put("maxDeckSize", maxSize);
            stats.put("avgDeckSize", avgSize);
        }

        return stats;
    }

    private String deckKey(UUID viewerId) {
        return DECK_KEY_PREFIX + viewerId.toString();
    }

    private String deckTsKey(UUID viewerId) {
        return DECK_TS_KEY_PREFIX + viewerId.toString();
    }
}
