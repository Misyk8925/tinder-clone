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
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DeckCache using Testcontainers with Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeckCacheIntegrationTest {

    static GenericContainer<?> redisContainer;

    static {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();

        System.setProperty("spring.data.redis.host", redisContainer.getHost());
        System.setProperty("spring.data.redis.port", String.valueOf(redisContainer.getFirstMappedPort()));
    }

    @Autowired
    private DeckCache deckCache;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private UUID testViewerId;

    @AfterAll
    static void tearDown() {
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        testViewerId = UUID.randomUUID();
        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();
    }

    @Test
    @DisplayName("Should write and read deck with correct order")
    void testWriteAndReadDeck() {
        // Given: A deck with 5 candidates with different scores
        UUID candidate1 = UUID.randomUUID();
        UUID candidate2 = UUID.randomUUID();
        UUID candidate3 = UUID.randomUUID();
        UUID candidate4 = UUID.randomUUID();
        UUID candidate5 = UUID.randomUUID();

        List<Entry<UUID, Double>> deck = List.of(
                Map.entry(candidate1, 10.0),
                Map.entry(candidate2, 50.0),
                Map.entry(candidate3, 30.0),
                Map.entry(candidate4, 20.0),
                Map.entry(candidate5, 40.0)
        );

        Duration ttl = Duration.ofMinutes(60);

        // When: Writing the deck
        StepVerifier.create(deckCache.writeDeck(testViewerId, deck, ttl))
                .verifyComplete();

        // Then: Reading should return candidates in descending score order
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 5))
                .expectNext(candidate2) // 50.0
                .expectNext(candidate5) // 40.0
                .expectNext(candidate3) // 30.0
                .expectNext(candidate4) // 20.0
                .expectNext(candidate1) // 10.0
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return correct deck size")
    void testDeckSize() {
        // Given: A deck with 3 candidates
        List<Entry<UUID, Double>> deck = List.of(
                Map.entry(UUID.randomUUID(), 10.0),
                Map.entry(UUID.randomUUID(), 20.0),
                Map.entry(UUID.randomUUID(), 30.0)
        );

        // When: Writing the deck
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // Then: Size should be 3
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(3L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return 0 size for non-existent deck")
    void testSizeForNonExistentDeck() {
        UUID nonExistentViewerId = UUID.randomUUID();

        StepVerifier.create(deckCache.size(nonExistentViewerId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void testPagination() {
        // Given: A deck with 10 candidates
        List<Entry<UUID, Double>> deck = new ArrayList<>();
        List<UUID> expectedOrder = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            deck.add(Map.entry(id, (double) i));
            expectedOrder.add(0, id); // Reverse order since we read by descending score
        }

        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: Reading first page (offset 0, limit 3)
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 3))
                .expectNext(expectedOrder.get(0))
                .expectNext(expectedOrder.get(1))
                .expectNext(expectedOrder.get(2))
                .verifyComplete();

        // When: Reading second page (offset 3, limit 3)
        StepVerifier.create(deckCache.readDeck(testViewerId, 3, 3))
                .expectNext(expectedOrder.get(3))
                .expectNext(expectedOrder.get(4))
                .expectNext(expectedOrder.get(5))
                .verifyComplete();

        // When: Reading last page (offset 7, limit 5) - should return only 3 items
        StepVerifier.create(deckCache.readDeck(testViewerId, 7, 5))
                .expectNext(expectedOrder.get(7))
                .expectNext(expectedOrder.get(8))
                .expectNext(expectedOrder.get(9))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should store and retrieve build timestamp")
    void testBuildTimestamp() {
        // Given: A deck
        List<Entry<UUID, Double>> deck = List.of(
                Map.entry(UUID.randomUUID(), 10.0)
        );

        // Truncate to milliseconds since System.currentTimeMillis() only has millisecond precision
        Instant beforeWrite = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        // When: Writing the deck
        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        Instant afterWrite = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusMillis(1);

        // Then: Build timestamp should be between beforeWrite and afterWrite
        StepVerifier.create(deckCache.getBuildInstant(testViewerId))
                .assertNext(optInstant -> {
                    assertThat(optInstant).isPresent();
                    Instant buildTime = optInstant.get();
                    assertThat(buildTime).isBetween(beforeWrite, afterWrite);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty optional for non-existent build timestamp")
    void testBuildTimestampForNonExistentDeck() {
        UUID nonExistentViewerId = UUID.randomUUID();

        StepVerifier.create(deckCache.getBuildInstant(nonExistentViewerId))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate deck and timestamp")
    void testInvalidate() {
        // Given: A deck with data
        List<Entry<UUID, Double>> deck = List.of(
                Map.entry(UUID.randomUUID(), 10.0),
                Map.entry(UUID.randomUUID(), 20.0)
        );

        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // Verify deck exists
        Long sizeBefore = deckCache.size(testViewerId).block();
        assertThat(sizeBefore).isEqualTo(2L);

        // When: Invalidating the deck
        StepVerifier.create(deckCache.invalidate(testViewerId))
                .expectNext(2L) // Should delete 2 keys (deck and timestamp)
                .verifyComplete();

        // Then: Deck should be empty
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(0L)
                .verifyComplete();

        // And: Timestamp should be gone
        StepVerifier.create(deckCache.getBuildInstant(testViewerId))
                .expectNext(Optional.empty())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should overwrite existing deck")
    void testOverwriteDeck() {
        // Given: An existing deck
        UUID oldCandidate = UUID.randomUUID();
        List<Entry<UUID, Double>> oldDeck = List.of(
                Map.entry(oldCandidate, 10.0)
        );

        deckCache.writeDeck(testViewerId, oldDeck, Duration.ofMinutes(60)).block();

        // When: Writing a new deck
        UUID newCandidate = UUID.randomUUID();
        List<Entry<UUID, Double>> newDeck = List.of(
                Map.entry(newCandidate, 20.0)
        );

        deckCache.writeDeck(testViewerId, newDeck, Duration.ofMinutes(60)).block();

        // Then: Only new deck should exist
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(newCandidate)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should read top N candidates")
    void testReadTop() {
        // Given: A deck with 5 candidates
        List<UUID> candidates = new ArrayList<>();
        List<Entry<UUID, Double>> deck = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            candidates.add(0, id); // Reverse order
            deck.add(Map.entry(id, (double) i));
        }

        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: Reading top 3
        StepVerifier.create(deckCache.readTop(testViewerId, 3))
                .assertNext(list -> {
                    assertThat(list).hasSize(3);
                    assertThat(list.get(0)).isEqualTo(candidates.get(0));
                    assertThat(list.get(1)).isEqualTo(candidates.get(1));
                    assertThat(list.get(2)).isEqualTo(candidates.get(2));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should read range with scores")
    void testReadRangeWithScores() {
        // Given: A deck with known scores
        UUID candidate1 = UUID.randomUUID();
        UUID candidate2 = UUID.randomUUID();
        UUID candidate3 = UUID.randomUUID();

        List<Entry<UUID, Double>> deck = List.of(
                Map.entry(candidate1, 10.0),
                Map.entry(candidate2, 20.0),
                Map.entry(candidate3, 30.0)
        );

        deckCache.writeDeck(testViewerId, deck, Duration.ofMinutes(60)).block();

        // When: Reading range with scores
        StepVerifier.create(deckCache.readRangeWithScores(testViewerId, 0, 2))
                .assertNext(entry -> {
                    assertThat(entry.getKey()).isEqualTo(candidate3);
                    assertThat(entry.getValue()).isEqualTo(30.0);
                })
                .assertNext(entry -> {
                    assertThat(entry.getKey()).isEqualTo(candidate2);
                    assertThat(entry.getValue()).isEqualTo(20.0);
                })
                .assertNext(entry -> {
                    assertThat(entry.getKey()).isEqualTo(candidate1);
                    assertThat(entry.getValue()).isEqualTo(10.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty deck read")
    void testReadEmptyDeck() {
        UUID nonExistentViewerId = UUID.randomUUID();

        StepVerifier.create(deckCache.readDeck(nonExistentViewerId, 0, 10))
                .verifyComplete();
    }
}

