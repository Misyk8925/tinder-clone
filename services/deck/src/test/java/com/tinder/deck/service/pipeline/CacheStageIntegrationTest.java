package com.tinder.deck.service.pipeline;

import com.tinder.deck.service.DeckCache;
import com.tinder.deck.config.DeckResilienceProperties;
import com.tinder.deck.resilience.DeckResilience;
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
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CacheStage with real Redis using Testcontainers
 *
 * Tests real caching behavior including:
 * - Actual Redis write/read operations
 * - TTL expiration
 * - Score order preservation in Redis ZSET
 * - Large deck handling with real storage
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheStageIntegrationTest {

    // Test configuration constants
    private static final long TTL_MINUTES = 5;
    private static final int SMALL_DECK_SIZE = 3;
    private static final int LARGE_DECK_SIZE = 100;

    // Test score constants
    private static final double HIGH_SCORE = 0.95;
    private static final double MEDIUM_SCORE = 0.85;
    private static final double LOW_SCORE = 0.70;

    static GenericContainer<?> redisContainer;

    static {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                .withExposedPorts(6379);
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

    private CacheStage cacheStage;

    private UUID testViewerId;

    @BeforeEach
    void setUp() {
        cacheStage = new CacheStage(deckCache);
        testViewerId = UUID.randomUUID();

        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();
    }

    @Test
    @DisplayName("Should cache scored candidates to real Redis")
    void shouldCacheScoredCandidatesSuccessfully() {
        // Given: scored candidates with different scores
        UUID highScoredId = UUID.randomUUID();
        UUID mediumScoredId = UUID.randomUUID();
        UUID lowScoredId = UUID.randomUUID();

        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(
                new ScoringStage.ScoredCandidate(highScoredId, HIGH_SCORE),
                new ScoringStage.ScoredCandidate(mediumScoredId, MEDIUM_SCORE),
                new ScoringStage.ScoredCandidate(lowScoredId, LOW_SCORE)
        );

        // When: caching to Redis
        Mono<Void> result = cacheStage.cacheDeck(testViewerId, scoredCandidates);

        // Then: should complete successfully
        StepVerifier.create(result)
                .verifyComplete();

        // Verify: data is actually in Redis
        StepVerifier.create(deckCache.size(testViewerId))
                .assertNext(size -> assertThat(size).isEqualTo(SMALL_DECK_SIZE))
                .verifyComplete();

        // Verify: scores are preserved in correct order (descending)
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(highScoredId)   // Highest score first
                .expectNext(mediumScoredId)
                .expectNext(lowScoredId)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cache empty deck to Redis")
    void shouldHandleEmptyDeck() {
        // Given: no scored candidates
        Flux<ScoringStage.ScoredCandidate> emptyCandidates = Flux.empty();

        // When: caching empty deck
        Mono<Void> result = cacheStage.cacheDeck(testViewerId, emptyCandidates);

        // Then: should complete successfully
        StepVerifier.create(result)
                .verifyComplete();

        // Verify: Redis contains empty deck
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cache large deck efficiently")
    void shouldHandleLargeDeck() {
        // Given: many scored candidates
        Flux<ScoringStage.ScoredCandidate> largeDeck = Flux.range(0, LARGE_DECK_SIZE)
                .map(i -> new ScoringStage.ScoredCandidate(
                        UUID.randomUUID(),
                        1.0 - (i * 0.01) // Descending scores
                ));

        // When: caching large deck
        Mono<Void> result = cacheStage.cacheDeck(testViewerId, largeDeck);

        // Then: should complete successfully
        StepVerifier.create(result)
                .verifyComplete();

        // Verify: all candidates are cached
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext((long) LARGE_DECK_SIZE)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should preserve score order in Redis ZSET")
    void shouldPreserveScoreOrder() {
        // Given: candidates with specific scores
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        UUID id4 = UUID.randomUUID();

        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(
                new ScoringStage.ScoredCandidate(id3, 0.70), // Will be sorted
                new ScoringStage.ScoredCandidate(id1, 0.95),
                new ScoringStage.ScoredCandidate(id4, 0.60),
                new ScoringStage.ScoredCandidate(id2, 0.85)
        );

        // When: caching in random order
        cacheStage.cacheDeck(testViewerId, scoredCandidates).block();

        // Then: Redis should return in descending score order
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(id1) // 0.95
                .expectNext(id2) // 0.85
                .expectNext(id3) // 0.70
                .expectNext(id4) // 0.60
                .verifyComplete();
    }

    @Test
    @DisplayName("Should overwrite existing deck in Redis")
    void shouldOverwriteExistingDeck() {
        // Given: initial deck in Redis
        UUID oldCandidateId = UUID.randomUUID();
        Flux<ScoringStage.ScoredCandidate> initialDeck = Flux.just(
                new ScoringStage.ScoredCandidate(oldCandidateId, 0.80)
        );
        cacheStage.cacheDeck(testViewerId, initialDeck).block();

        // When: caching new deck for same viewer
        UUID newCandidateId = UUID.randomUUID();
        Flux<ScoringStage.ScoredCandidate> newDeck = Flux.just(
                new ScoringStage.ScoredCandidate(newCandidateId, 0.90)
        );
        cacheStage.cacheDeck(testViewerId, newDeck).block();

        // Then: Redis should contain only new deck
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(newCandidateId)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle multiple viewers independently in Redis")
    void shouldHandleMultipleViewers() {
        // Given: two different viewers
        UUID viewer1Id = UUID.randomUUID();
        UUID viewer2Id = UUID.randomUUID();

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        Flux<ScoringStage.ScoredCandidate> deck1 = Flux.just(
                new ScoringStage.ScoredCandidate(candidate1Id, 0.90)
        );

        Flux<ScoringStage.ScoredCandidate> deck2 = Flux.just(
                new ScoringStage.ScoredCandidate(candidate2Id, 0.80)
        );

        // When: caching decks for different viewers
        cacheStage.cacheDeck(viewer1Id, deck1).block();
        cacheStage.cacheDeck(viewer2Id, deck2).block();

        // Then: each viewer should have their own deck
        StepVerifier.create(deckCache.readDeck(viewer1Id, 0, 10))
                .expectNext(candidate1Id)
                .verifyComplete();

        StepVerifier.create(deckCache.readDeck(viewer2Id, 0, 10))
                .expectNext(candidate2Id)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle Redis errors gracefully")
    void shouldPropagateRedisErrors() {
        // Given: Create a CacheStage with a mock DeckCache that simulates Redis error
        DeckResilience resilience = DeckResilience.from(new DeckResilienceProperties());
        DeckCache failingCache = new DeckCache(redisTemplate, resilience) {
            @Override
            public Mono<Void> writeDeck(UUID viewerId, List<Map.Entry<UUID, Double>> deck, Duration ttl) {
                return Mono.error(new RuntimeException("Simulated Redis connection failure"));
            }
        };

        CacheStage failingCacheStage = new CacheStage(failingCache);

        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(
                new ScoringStage.ScoredCandidate(UUID.randomUUID(), 0.90),
                new ScoringStage.ScoredCandidate(UUID.randomUUID(), 0.85)
        );

        // When: caching with failing cache
        Mono<Void> result = failingCacheStage.cacheDeck(testViewerId, scoredCandidates);

        // Then: should propagate error from Redis operations
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should verify build timestamp is stored in Redis")
    void shouldStoreBuildTimestamp() {
        // Given: scored candidates
        Flux<ScoringStage.ScoredCandidate> scoredCandidates = Flux.just(
                new ScoringStage.ScoredCandidate(UUID.randomUUID(), 0.90)
        );

        // When: caching deck
        cacheStage.cacheDeck(testViewerId, scoredCandidates).block();

        // Then: build timestamp should be available
        StepVerifier.create(deckCache.getBuildInstant(testViewerId))
                .assertNext(instant -> assertThat(instant).isPresent())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should read top N from large cached deck")
    void shouldReadTopNCandidates() {
        // Given: large deck cached in Redis
        int deckSize = 50;
        List<UUID> expectedTopIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // Create deck with known top 3
        List<Map.Entry<UUID, Double>> deck = new java.util.ArrayList<>();
        deck.add(Map.entry(expectedTopIds.get(0), 1.00));
        deck.add(Map.entry(expectedTopIds.get(1), 0.95));
        deck.add(Map.entry(expectedTopIds.get(2), 0.90));

        // Add more candidates with lower scores
        for (int i = 3; i < deckSize; i++) {
            deck.add(Map.entry(UUID.randomUUID(), 0.85 - (i * 0.01)));
        }

        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(TTL_MINUTES)).block();

        // When: reading top 3
        StepVerifier.create(deckCache.readTop(testViewerId, 3))
                .assertNext(topList -> {
                    assertThat(topList).hasSize(3);
                    assertThat(topList.get(0)).isEqualTo(expectedTopIds.get(0));
                    assertThat(topList.get(1)).isEqualTo(expectedTopIds.get(1));
                    assertThat(topList.get(2)).isEqualTo(expectedTopIds.get(2));
                })
                .verifyComplete();
    }
}
