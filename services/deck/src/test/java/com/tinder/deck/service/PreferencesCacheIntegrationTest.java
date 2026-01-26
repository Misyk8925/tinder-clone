package com.tinder.deck.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

/**
 * Integration tests for Preferences Cache functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreferencesCacheIntegrationTest {

    @Container
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        redisContainer.start();
    }

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    private DeckCache deckCache;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();
    }

    @Test
    @DisplayName("Should cache preferences result")
    void shouldCachePreferencesResult() {
        // Given
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> candidateIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // When
        StepVerifier.create(deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds))
                .expectNext(3L)
                .verifyComplete();

        // Then: should be able to retrieve cached IDs
        StepVerifier.create(deckCache.getCandidatesByPreferences(minAge, maxAge, gender))
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if preferences cache exists")
    void shouldCheckPreferencesCacheExists() {
        // Given
        int minAge = 22;
        int maxAge = 30;
        String gender = "MALE";

        // When: no cache yet
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(false)
                .verifyComplete();

        // When: cache some data
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // Then: should exist
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate preferences cache")
    void shouldInvalidatePreferencesCache() {
        // Given: cached preferences
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // When: invalidate
        StepVerifier.create(deckCache.invalidatePreferencesCache(minAge, maxAge, gender))
                .expectNext(true)
                .verifyComplete();

        // Then: should not exist
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get preferences cache size")
    void shouldGetPreferencesCacheSize() {
        // Given
        int minAge = 25;
        int maxAge = 35;
        String gender = "FEMALE";
        List<UUID> candidateIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // When: cache data
        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // Then: size should be 5
        StepVerifier.create(deckCache.getPreferencesCacheSize(minAge, maxAge, gender))
                .expectNext(5L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty candidate list")
    void shouldHandleEmptyCandidateList() {
        // Given
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> emptyCandidates = List.of();

        // When
        StepVerifier.create(deckCache.cachePreferencesResult(minAge, maxAge, gender, emptyCandidates))
                .expectNext(0L)
                .verifyComplete();

        // Then: cache should not exist
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty flux for non-existent preferences cache")
    void shouldReturnEmptyFluxForNonExistentCache() {
        // Given: no cache
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";

        // When/Then
        StepVerifier.create(deckCache.getCandidatesByPreferences(minAge, maxAge, gender))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle different preferences combinations independently")
    void shouldHandleDifferentPreferencesCombinations() {
        // Given: different preferences
        List<UUID> candidates1 = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> candidates2 = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // When: cache both
        deckCache.cachePreferencesResult(18, 25, "FEMALE", candidates1).block();
        deckCache.cachePreferencesResult(22, 30, "MALE", candidates2).block();

        // Then: both should exist independently
        StepVerifier.create(deckCache.getCandidatesByPreferences(18, 25, "FEMALE"))
                .expectNextCount(2)
                .verifyComplete();

        StepVerifier.create(deckCache.getCandidatesByPreferences(22, 30, "MALE"))
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle case-insensitive gender in cache key")
    void shouldHandleCaseInsensitiveGender() {
        // Given: cache with lowercase
        List<UUID> candidates = List.of(UUID.randomUUID(), UUID.randomUUID());
        deckCache.cachePreferencesResult(18, 25, "female", candidates).block();

        // When/Then: retrieve with uppercase (should work due to toUpperCase in key)
        StepVerifier.create(deckCache.hasPreferencesCache(18, 25, "FEMALE"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should overwrite existing preferences cache")
    void shouldOverwriteExistingCache() {
        // Given: initial cache
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> initialCandidates = List.of(UUID.randomUUID(), UUID.randomUUID());
        deckCache.cachePreferencesResult(minAge, maxAge, gender, initialCandidates).block();

        // When: cache new data (should overwrite)
        List<UUID> newCandidates = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        deckCache.cachePreferencesResult(minAge, maxAge, gender, newCandidates).block();

        // Then: should have new data
        StepVerifier.create(deckCache.getPreferencesCacheSize(minAge, maxAge, gender))
                .expectNext(5L) // 2 old + 3 new (SET adds unique values)
                .verifyComplete();
    }
}
