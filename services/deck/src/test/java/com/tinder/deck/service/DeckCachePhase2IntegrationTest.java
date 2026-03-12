package com.tinder.deck.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;

/**
 * Integration tests for Phase 2 DeckCache enhancements:
 * - Stale tracking
 * - Distributed locking
 * - Filtered read methods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeckCachePhase2IntegrationTest {

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

    private UUID testViewerId;
    private List<UUID> testProfileIds;

    @BeforeEach
    void setUp() {
        testViewerId = UUID.randomUUID();
        testProfileIds = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();
    }

    // ==================== Stale Tracking Tests ====================

    @Test
    @DisplayName("Should mark profile as stale")
    void shouldMarkProfileAsStale() {
        // Given
        UUID profileId = testProfileIds.get(0);

        // When
        StepVerifier.create(deckCache.markAsStale(testViewerId, profileId))
                .expectNext(1L)
                .verifyComplete();

        // Then
        StepVerifier.create(deckCache.isStale(testViewerId, profileId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should detect non-stale profile")
    void shouldDetectNonStaleProfile() {
        // Given
        UUID profileId = testProfileIds.get(0);

        // When/Then
        StepVerifier.create(deckCache.isStale(testViewerId, profileId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get all stale profiles")
    void shouldGetAllStaleProfiles() {
        // Given: mark 3 profiles as stale
        UUID stale1 = testProfileIds.get(0);
        UUID stale2 = testProfileIds.get(1);
        UUID stale3 = testProfileIds.get(2);

        deckCache.markAsStale(testViewerId, stale1).block();
        deckCache.markAsStale(testViewerId, stale2).block();
        deckCache.markAsStale(testViewerId, stale3).block();

        // When/Then
        StepVerifier.create(deckCache.getStaleProfiles(testViewerId))
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should remove profile from stale set")
    void shouldRemoveStaleProfile() {
        // Given
        UUID profileId = testProfileIds.get(0);
        deckCache.markAsStale(testViewerId, profileId).block();

        // When
        StepVerifier.create(deckCache.removeStale(testViewerId, profileId))
                .expectNext(1L)
                .verifyComplete();

        // Then
        StepVerifier.create(deckCache.isStale(testViewerId, profileId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should clear all stale markers")
    void shouldClearAllStale() {
        // Given: mark multiple profiles as stale
        deckCache.markAsStale(testViewerId, testProfileIds.get(0)).block();
        deckCache.markAsStale(testViewerId, testProfileIds.get(1)).block();
        deckCache.markAsStale(testViewerId, testProfileIds.get(2)).block();

        // When
        StepVerifier.create(deckCache.clearStale(testViewerId))
                .expectNext(true)
                .verifyComplete();

        // Then: all should be non-stale
        StepVerifier.create(deckCache.getStaleProfiles(testViewerId))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle stale markers independently per viewer")
    void shouldHandleStalePerViewer() {
        // Given
        UUID viewer1 = UUID.randomUUID();
        UUID viewer2 = UUID.randomUUID();
        UUID profileId = testProfileIds.get(0);

        // When: mark stale only for viewer1
        deckCache.markAsStale(viewer1, profileId).block();

        // Then
        StepVerifier.create(deckCache.isStale(viewer1, profileId))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(deckCache.isStale(viewer2, profileId))
                .expectNext(false)
                .verifyComplete();
    }

    // ==================== Distributed Lock Tests ====================

    @Test
    @DisplayName("Should acquire lock successfully")
    void shouldAcquireLock() {
        // When/Then
        StepVerifier.create(deckCache.acquireLock(testViewerId))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fail to acquire lock when already held")
    void shouldFailToAcquireLockWhenHeld() {
        // Given: lock already acquired
        deckCache.acquireLock(testViewerId).block();

        // When/Then: second attempt should fail
        StepVerifier.create(deckCache.acquireLock(testViewerId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should release lock successfully")
    void shouldReleaseLock() {
        // Given
        deckCache.acquireLock(testViewerId).block();

        // When
        StepVerifier.create(deckCache.releaseLock(testViewerId))
                .expectNext(true)
                .verifyComplete();

        // Then
        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle lock with custom TTL")
    void shouldHandleLockWithCustomTTL() {
        // When: acquire lock with 1 second TTL
        StepVerifier.create(deckCache.acquireLock(testViewerId, Duration.ofSeconds(1)))
                .expectNext(true)
                .verifyComplete();

        // Then: lock should exist
        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(true)
                .verifyComplete();

        // Wait for TTL expiration
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: lock should be auto-released
        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should execute operation with lock protection")
    void shouldExecuteWithLock() {
        // Given: a test operation
        var testResult = "operation completed";

        // When: execute under lock
        StepVerifier.create(
                deckCache.withLock(testViewerId, reactor.core.publisher.Mono.just(testResult))
        )
                .expectNext(testResult)
                .verifyComplete();

        // Then: lock should be released after operation
        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should skip operation if lock cannot be acquired")
    void shouldSkipOperationIfLockHeld() {
        // Given: lock already held
        deckCache.acquireLock(testViewerId).block();

        // When: try to execute operation
        StepVerifier.create(
                deckCache.withLock(testViewerId, reactor.core.publisher.Mono.just("result"))
        )
                .verifyComplete(); // Should complete empty
    }

    @Test
    @DisplayName("Should release lock even if operation fails")
    void shouldReleaseLockOnError() {
        // Given: operation that will fail
        var failingOperation = reactor.core.publisher.Mono.<String>error(
                new RuntimeException("Test error")
        );

        // When: execute under lock
        deckCache.withLock(testViewerId, failingOperation)
                .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                .block();

        // Then: lock should still be released
        // Wait a bit for async cleanup
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        StepVerifier.create(deckCache.isLocked(testViewerId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle locks independently per viewer")
    void shouldHandleLocksPerViewer() {
        // Given
        UUID viewer1 = UUID.randomUUID();
        UUID viewer2 = UUID.randomUUID();

        // When: acquire lock for viewer1
        deckCache.acquireLock(viewer1).block();

        // Then: viewer2 should be able to acquire their lock
        StepVerifier.create(deckCache.acquireLock(viewer2))
                .expectNext(true)
                .verifyComplete();
    }

    // ==================== Filtered Read Tests ====================

    @Test
    @DisplayName("Should read deck excluding stale profiles")
    void shouldReadDeckExcludingStale() {
        // Given: deck with 5 profiles
        List<Map.Entry<UUID, Double>> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deck.add(Map.entry(testProfileIds.get(i), 0.9 - (i * 0.1)));
        }
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // Mark 2 profiles as stale
        deckCache.markAsStale(testViewerId, testProfileIds.get(1)).block();
        deckCache.markAsStale(testViewerId, testProfileIds.get(3)).block();

        // When: read deck excluding stale
        StepVerifier.create(deckCache.readDeckExcludingStale(testViewerId, 0, 10))
                .expectNext(testProfileIds.get(0)) // Fresh
                .expectNext(testProfileIds.get(2)) // Fresh
                .expectNext(testProfileIds.get(4)) // Fresh
                .verifyComplete();
    }

    @Test
    @DisplayName("Should read top N excluding stale")
    void shouldReadTopExcludingStale() {
        // Given: deck with 5 profiles
        List<Map.Entry<UUID, Double>> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deck.add(Map.entry(testProfileIds.get(i), 0.9 - (i * 0.1)));
        }
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // Mark first profile as stale
        deckCache.markAsStale(testViewerId, testProfileIds.get(0)).block();

        // When: read top 2
        List<UUID> result = deckCache.readTopExcludingStale(testViewerId, 2).block();

        // Then: should get profiles 1 and 2 (skipping stale profile 0)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(testProfileIds.get(1), result.get(0));
        Assertions.assertEquals(testProfileIds.get(2), result.get(1));
    }

    @Test
    @DisplayName("Should read deck normally when no stale profiles")
    void shouldReadNormallyWithoutStale() {
        // Given: deck with 3 profiles, none stale
        List<Map.Entry<UUID, Double>> deck = List.of(
                Map.entry(testProfileIds.get(0), 0.9),
                Map.entry(testProfileIds.get(1), 0.8),
                Map.entry(testProfileIds.get(2), 0.7)
        );
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: read excluding stale
        StepVerifier.create(deckCache.readDeckExcludingStale(testViewerId, 0, 10))
                .expectNext(testProfileIds.get(0))
                .expectNext(testProfileIds.get(1))
                .expectNext(testProfileIds.get(2))
                .verifyComplete();
    }

    // ==================== Remove from Deck Tests ====================

    @Test
    @DisplayName("Should remove single profile from deck")
    void shouldRemoveSingleProfile() {
        // Given: deck with 3 profiles
        List<Map.Entry<UUID, Double>> deck = List.of(
                Map.entry(testProfileIds.get(0), 0.9),
                Map.entry(testProfileIds.get(1), 0.8),
                Map.entry(testProfileIds.get(2), 0.7)
        );
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: remove one profile
        StepVerifier.create(deckCache.removeFromDeck(testViewerId, testProfileIds.get(1)))
                .expectNext(1L)
                .verifyComplete();

        // Then: deck should have 2 profiles
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(testProfileIds.get(0))
                .expectNext(testProfileIds.get(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should remove multiple profiles from deck")
    void shouldRemoveMultipleProfiles() {
        // Given: deck with 5 profiles
        List<Map.Entry<UUID, Double>> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deck.add(Map.entry(testProfileIds.get(i), 0.9 - (i * 0.1)));
        }
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: remove 3 profiles
        Set<UUID> toRemove = Set.of(
                testProfileIds.get(1),
                testProfileIds.get(2),
                testProfileIds.get(4)
        );

        StepVerifier.create(deckCache.removeMultipleFromDeck(testViewerId, toRemove))
                .expectNext(3L)
                .verifyComplete();

        // Then: deck should have 2 profiles left
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle removing non-existent profile")
    void shouldHandleRemovingNonExistentProfile() {
        // Given: empty deck
        UUID nonExistentId = UUID.randomUUID();

        // When/Then: should return 0
        StepVerifier.create(deckCache.removeFromDeck(testViewerId, nonExistentId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if deck exists")
    void shouldCheckDeckExists() {
        // Given: no deck
        StepVerifier.create(deckCache.exists(testViewerId))
                .expectNext(false)
                .verifyComplete();

        // When: create deck
        List<Map.Entry<UUID, Double>> deck = List.of(
                Map.entry(testProfileIds.get(0), 0.9)
        );
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // Then: should exist
        StepVerifier.create(deckCache.exists(testViewerId))
                .expectNext(true)
                .verifyComplete();
    }

    // ==================== Integration Scenario Tests ====================

    @Test
    @DisplayName("Integration: Stale tracking + Filtered read")
    void integrationStaleTrackingAndFilteredRead() {
        // Given: deck with profiles
        List<Map.Entry<UUID, Double>> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            deck.add(Map.entry(testProfileIds.get(i), 0.9 - (i * 0.1)));
        }
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: profile changes critically (e.g., age changed)
        UUID changedProfile = testProfileIds.get(2);
        deckCache.markAsStale(testViewerId, changedProfile).block();

        // Then: reading deck should exclude stale profile
        List<UUID> freshProfiles = deckCache.readTopExcludingStale(testViewerId, 5).block();

        Assertions.assertNotNull(freshProfiles);
        Assertions.assertEquals(4, freshProfiles.size());
        Assertions.assertFalse(freshProfiles.contains(changedProfile));
    }

    @Test
    @DisplayName("Integration: Lock protection during rebuild")
    void integrationLockDuringRebuild() {
        // Simulate concurrent rebuild attempts
        UUID viewer = UUID.randomUUID();

        // First rebuild acquires lock
        var firstRebuild = deckCache.withLock(viewer,
                reactor.core.publisher.Mono.delay(Duration.ofMillis(100))
                        .thenReturn("first")
        );

        // Second rebuild tries immediately (should fail to acquire lock)
        var secondRebuild = deckCache.withLock(viewer,
                reactor.core.publisher.Mono.just("second")
        );

        // Execute concurrently
        StepVerifier.create(reactor.core.publisher.Flux.merge(firstRebuild, secondRebuild))
                .expectNext("first")
                .verifyComplete(); // Second should be skipped
    }

    @Test
    @DisplayName("Integration: Swipe removes profile from deck")
    void integrationSwipeRemovesProfile() {
        // Given: user has deck with profiles
        List<Map.Entry<UUID, Double>> deck = List.of(
                Map.entry(testProfileIds.get(0), 0.9),
                Map.entry(testProfileIds.get(1), 0.8),
                Map.entry(testProfileIds.get(2), 0.7)
        );
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: user swipes on profile
        UUID swipedProfile = testProfileIds.get(1);
        deckCache.removeFromDeck(testViewerId, swipedProfile).block();

        // Then: profile should not appear in deck anymore
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(testProfileIds.get(0))
                .expectNext(testProfileIds.get(2))
                .verifyComplete();
    }
}
