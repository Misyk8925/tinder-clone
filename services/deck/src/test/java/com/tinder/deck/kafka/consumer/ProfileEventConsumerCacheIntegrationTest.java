package com.tinder.deck.kafka.consumer;

import com.tinder.contracts.event.v1.ChangeType;
import com.tinder.contracts.event.v1.ProfileCreatedEvent;
import com.tinder.contracts.event.v1.ProfileUpdatedEvent;
import com.tinder.deck.service.DeckCache;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Integration tests for ProfileEventConsumer cache invalidation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileEventConsumerCacheIntegrationTest {

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
    private ProfileEventConsumer consumer;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();
    }

    @Test
    @DisplayName("Should NOT invalidate preferences cache on PREFERENCES change (only personal deck)")
    void shouldNotInvalidatePreferencesCacheOnPreferencesChange() throws InterruptedException {
        // Given: Preferences cache exists
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // Verify cache exists
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(true)
                .verifyComplete();

        // When: ProfileEvent with PREFERENCES change (user changes their preferences)
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.PREFERENCES,
                Set.of("preferences.minAge", "preferences.maxAge"), Instant.now(),
                "minAge:18,maxAge:25,gender:FEMALE");

        consumer.consumeProfileUpdate(event, 0, 1L);

        // Then: Preferences cache should STILL EXIST (not invalidated)
        // Reason: User changing their preferences doesn't affect the candidates pool
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(true) // Still true!
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate personal deck cache on PREFERENCES change")
    void shouldInvalidatePersonalDeckOnPreferencesChange() throws InterruptedException {
        // Given: Personal deck exists
        UUID profileId = UUID.randomUUID();
        List<java.util.Map.Entry<UUID, Double>> deck = List.of(
                java.util.Map.entry(UUID.randomUUID(), 0.9),
                java.util.Map.entry(UUID.randomUUID(), 0.8)
        );

        deckCache.writeDeck(profileId, deck, java.time.Duration.ofMinutes(60)).block();

        // Verify deck exists
        StepVerifier.create(deckCache.exists(profileId))
                .expectNext(true)
                .verifyComplete();

        // When: ProfileEvent with PREFERENCES change
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.PREFERENCES,
                Set.of("preferences"), Instant.now(), "minAge:22,maxAge:30,gender:MALE");

        consumer.consumeProfileUpdate(event, 0, 1L);

        // Then: Personal deck should be invalidated
        StepVerifier.create(deckCache.exists(profileId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate preferences cache on CRITICAL_FIELDS change")
    void shouldInvalidatePreferencesCacheOnCriticalFieldsChange() throws InterruptedException {
        // Given: Preferences cache exists
        int minAge = 22;
        int maxAge = 30;
        String gender = "MALE";
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // When: ProfileEvent with CRITICAL_FIELDS change
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.CRITICAL_FIELDS,
                Set.of("age", "gender"), Instant.now(), "minAge:22,maxAge:30,gender:MALE");

        consumer.consumeProfileUpdate(event, 0, 1L);

        // Then: Preferences cache should be invalidated
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should NOT invalidate cache on NON_CRITICAL change")
    void shouldNotInvalidateCacheOnNonCriticalChange() throws InterruptedException {
        // Given: Preferences cache exists
        int minAge = 25;
        int maxAge = 35;
        String gender = "ANY";
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // When: ProfileEvent with NON_CRITICAL change
        UUID profileId = UUID.randomUUID();
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.NON_CRITICAL,
                Set.of("bio", "name"), Instant.now(), null);

        consumer.consumeProfileUpdate(event, 0, 1L);

        // Then: Preferences cache should still exist
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle missing metadata gracefully")
    void shouldHandleMissingMetadataGracefully() throws InterruptedException {
        // Given: Personal deck exists
        UUID profileId = UUID.randomUUID();
        List<java.util.Map.Entry<UUID, Double>> deck = List.of(
                java.util.Map.entry(UUID.randomUUID(), 0.9)
        );

        deckCache.writeDeck(profileId, deck, java.time.Duration.ofMinutes(60)).block();

        // When: ProfileEvent without metadata
        ProfileUpdatedEvent event = new ProfileUpdatedEvent(
                UUID.randomUUID(), profileId, ChangeType.PREFERENCES,
                Set.of("preferences"), Instant.now(), null);

        consumer.consumeProfileUpdate(event, 0, 1L);

        // Then: Personal deck should still be invalidated
        StepVerifier.create(deckCache.exists(profileId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should invalidate preferences cache on profile creation")
    void shouldInvalidatePreferencesCacheOnProfileCreation() {
        // Given: Preferences cache exists with candidates
        int minAge = 18;
        int maxAge = 25;
        String gender = "FEMALE";
        List<UUID> candidateIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        deckCache.cachePreferencesResult(minAge, maxAge, gender, candidateIds).block();

        // When: A new profile is created
        ProfileCreatedEvent event = new ProfileCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "new-user-id", Instant.now());

        consumer.consumeProfileCreation(event, 0, 1L);

        // Then: Preferences cache should be invalidated so the new profile is discovered
        StepVerifier.create(deckCache.hasPreferencesCache(minAge, maxAge, gender))
                .expectNext(false)
                .verifyComplete();
    }
}
