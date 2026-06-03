package com.tinder.deck.service;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code deck:contains:{profileId}} reverse index and the
 * reverse-index-backed {@link DeckCache#removeFromAllDecks(UUID)} fan-out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeckCacheReverseIndexIntegrationTest {

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

    private static final Duration TTL = Duration.ofMinutes(60);

    @BeforeEach
    void setUp() {
        redisTemplate.execute(connection -> connection.serverCommands().flushAll()).blockLast();
    }

    private Set<String> reverseIndex(UUID profileId) {
        return redisTemplate.opsForSet()
                .members("deck:contains:" + profileId)
                .collect(java.util.stream.Collectors.toSet())
                .block();
    }

    @Test
    @DisplayName("writeDeck records the viewer in each profile's reverse index")
    void writeDeckPopulatesReverseIndex() {
        UUID viewer = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        List<Entry<UUID, Double>> deck = List.of(Map.entry(p1, 10.0), Map.entry(p2, 20.0));
        deckCache.writeDeck(viewer, deck, TTL).block();

        assertThat(reverseIndex(p1)).containsExactly(viewer.toString());
        assertThat(reverseIndex(p2)).containsExactly(viewer.toString());
    }

    @Test
    @DisplayName("a profile in multiple viewers' decks lists all of them in its reverse index")
    void reverseIndexAccumulatesAcrossViewers() {
        UUID viewerA = UUID.randomUUID();
        UUID viewerB = UUID.randomUUID();
        UUID shared = UUID.randomUUID();

        deckCache.writeDeck(viewerA, List.of(Map.entry(shared, 10.0)), TTL).block();
        deckCache.writeDeck(viewerB, List.of(Map.entry(shared, 30.0)), TTL).block();

        assertThat(reverseIndex(shared))
                .containsExactlyInAnyOrder(viewerA.toString(), viewerB.toString());
    }

    @Test
    @DisplayName("removeFromAllDecks purges the profile from exactly the decks that contain it")
    void removeFromAllDecksPurgesAffectedDecksOnly() {
        UUID viewerA = UUID.randomUUID();
        UUID viewerB = UUID.randomUUID();
        UUID viewerC = UUID.randomUUID();
        UUID target = UUID.randomUUID();   // in A and B
        UUID other = UUID.randomUUID();    // in C only

        deckCache.writeDeck(viewerA, List.of(Map.entry(target, 10.0), Map.entry(UUID.randomUUID(), 5.0)), TTL).block();
        deckCache.writeDeck(viewerB, List.of(Map.entry(target, 20.0)), TTL).block();
        deckCache.writeDeck(viewerC, List.of(Map.entry(other, 15.0)), TTL).block();

        Long affected = deckCache.removeFromAllDecks(target).block();

        // Touched exactly the two decks that contained the target.
        assertThat(affected).isEqualTo(2L);
        assertThat(deckCache.readDeck(viewerA, 0, 10).collectList().block()).doesNotContain(target);
        assertThat(deckCache.readDeck(viewerB, 0, 10).collectList().block()).isEmpty();
        // Unrelated deck is untouched.
        assertThat(deckCache.readDeck(viewerC, 0, 10).collectList().block()).containsExactly(other);
        // Reverse index for the purged profile is cleaned up.
        assertThat(reverseIndex(target)).isEmpty();
    }

    @Test
    @DisplayName("removeFromAllDecks on a profile in no deck returns 0 and does not error")
    void removeFromAllDecksNoIndexIsSafe() {
        Long affected = deckCache.removeFromAllDecks(UUID.randomUUID()).block();
        assertThat(affected).isZero();
    }
}
