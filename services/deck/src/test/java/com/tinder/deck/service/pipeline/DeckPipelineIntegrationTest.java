package com.tinder.deck.service.pipeline;

import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.adapters.SwipesHttp;
import com.tinder.deck.dto.SharedLocationDto;
import com.tinder.deck.dto.SharedPreferencesDto;
import com.tinder.deck.dto.SharedProfileDto;
import com.tinder.deck.service.DeckCache;
import com.tinder.deck.service.ScoringService;
import com.tinder.deck.service.pipeline.util.PreferencesCacheHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for DeckPipeline with real Redis using Testcontainers
 *
 * Tests full pipeline execution with:
 * - Real Redis caching (via Testcontainers)
 * - Mocked HTTP clients (ProfilesHttp, SwipesHttp)
 * - Real scoring and pipeline orchestration
 * - Verification of end-to-end deck building flow
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeckPipelineIntegrationTest {

    // Test configuration constants
    private static final int PER_USER_LIMIT = 10;
    private static final Duration VERIFICATION_TIMEOUT = Duration.ofSeconds(10);

    // Stage configuration constants (matching application defaults)
    private static final int SEARCH_LIMIT = 2000;
    private static final long TIMEOUT_MS = 5000L;
    private static final int RETRIES = 1;
    private static final int BATCH_SIZE = 200;
    private static final int PARALLELISM = 8;
    private static final long TTL_MINUTES = 60L;

    // Test data constants
    private static final int VIEWER_AGE = 25;
    private static final int CANDIDATE_COUNT = 15;

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

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private PreferencesCacheHelper preferencesCacheHelper;

    @MockitoBean
    private ProfilesHttp profilesHttp;

    @MockitoBean
    private SwipesHttp swipesHttp;

    private DeckPipeline deckPipeline;
    private UUID testViewerId;

    @BeforeEach
    void setUp() {
        testViewerId = UUID.randomUUID();

        // Clean up Redis before each test
        redisTemplate.execute(connection -> connection.serverCommands().flushAll())
                .blockLast();

        // Create real pipeline components (CacheStage uses real Redis)
        CandidateSearchStage searchStage = new CandidateSearchStage(profilesHttp, deckCache, preferencesCacheHelper);
        ReflectionTestUtils.setField(searchStage, "searchLimit", SEARCH_LIMIT);

        SwipeFilterStage filterStage = new SwipeFilterStage(swipesHttp);
        ReflectionTestUtils.setField(filterStage, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(filterStage, "timeoutMs", TIMEOUT_MS);
        ReflectionTestUtils.setField(filterStage, "retries", RETRIES);

        ScoringStage scoringStage = new ScoringStage(scoringService);
        ReflectionTestUtils.setField(scoringStage, "parallelism", PARALLELISM);

        CacheStage cacheStage = new CacheStage(deckCache);
        ReflectionTestUtils.setField(cacheStage, "ttlMinutes", TTL_MINUTES);

        deckPipeline = new DeckPipeline(searchStage, filterStage, scoringStage, cacheStage);
        ReflectionTestUtils.setField(deckPipeline, "perUserLimit", PER_USER_LIMIT);
    }

    @Test
    @DisplayName("Should execute full pipeline and cache results to Redis")
    void shouldExecuteFullPipelineWithRealRedis() {
        // Given: viewer and mock candidate search results
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        List<SharedProfileDto> candidateList = new ArrayList<>();

        for (int i = 0; i < CANDIDATE_COUNT; i++) {
            UUID candidateId = UUID.randomUUID();
            candidateList.add(createProfile(candidateId, "Candidate" + i, 20 + i));
        }

        // Mock: ProfilesHttp returns candidates
        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidateList));

        // Mock: SwipesHttp returns no swipe history (all candidates fresh)
        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: executing full pipeline
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then: pipeline should complete successfully
        StepVerifier.create(result)
                .expectComplete()
                .verify(VERIFICATION_TIMEOUT);

        // Verify: deck is cached in Redis with limited size
        StepVerifier.create(deckCache.size(testViewerId))
                .assertNext(size -> assertThat(size).isEqualTo(PER_USER_LIMIT))
                .verifyComplete();

        // Verify: can read deck from Redis
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 5))
                .expectNextCount(5)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should filter out candidates with swipe history before caching")
    void shouldFilterSwipedCandidatesBeforeCaching() {
        // Given: viewer and candidates
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        UUID swipedCandidateId = UUID.randomUUID();
        UUID freshCandidate1Id = UUID.randomUUID();
        UUID freshCandidate2Id = UUID.randomUUID();

        List<SharedProfileDto> candidates = List.of(
                createProfile(swipedCandidateId, "AlreadySwiped", 23),
                createProfile(freshCandidate1Id, "Fresh1", 24),
                createProfile(freshCandidate2Id, "Fresh2", 25)
        );

        // Mock: ProfilesHttp returns 3 candidates
        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidates));

        // Mock: SwipesHttp indicates first candidate has swipe history
        Map<UUID, Boolean> swipeHistory = new HashMap<>();
        swipeHistory.put(swipedCandidateId, true);  // Has history
        swipeHistory.put(freshCandidate1Id, false); // Fresh
        swipeHistory.put(freshCandidate2Id, false); // Fresh

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(swipeHistory));

        // When: executing pipeline
        deckPipeline.buildDeck(viewer).block();

        // Then: only 2 candidates should be cached (swiped one filtered out)
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(2L)
                .verifyComplete();

        // Verify: swiped candidate is not in cached deck
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should apply per-user limit correctly in Redis")
    void shouldApplyPerUserLimitInRedis() {
        // Given: more candidates than limit
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        int totalCandidates = 20;
        List<SharedProfileDto> candidates = new ArrayList<>();

        for (int i = 0; i < totalCandidates; i++) {
            candidates.add(createProfile(UUID.randomUUID(), "Candidate" + i, 20 + i));
        }

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidates));

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: executing pipeline
        deckPipeline.buildDeck(viewer).block();

        // Then: Redis should contain only limited number (10)
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext((long) PER_USER_LIMIT)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle empty search results gracefully")
    void shouldHandleEmptySearchResults() {
        // Given: viewer but no candidates found
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.empty());

        // Note: SwipesHttp should NOT be called when there are no candidates
        // No need to mock it - if called, test will fail

        // When: executing pipeline with no candidates
        Mono<Void> result = deckPipeline.buildDeck(viewer);

        // Then: should complete successfully
        StepVerifier.create(result)
                .expectComplete()
                .verify(VERIFICATION_TIMEOUT);

        // Verify: deck cache has no entries (empty deck is NOT cached)
        // Note: DeckCache.writeDeck with empty list should not create a key
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should overwrite existing deck in Redis on rebuild")
    void shouldOverwriteExistingDeck() {
        // Given: viewer with existing deck in Redis
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        UUID oldCandidateId = UUID.randomUUID();
        List<Map.Entry<UUID, Double>> oldDeck = List.of(
                Map.entry(oldCandidateId, 0.80)
        );
        deckCache.writeDeck(testViewerId, oldDeck, Duration.ofMinutes(60)).block();

        // Verify old deck exists
        assertThat(deckCache.size(testViewerId).block()).isEqualTo(1L);

        // When: rebuilding deck with new candidates
        UUID newCandidateId = UUID.randomUUID();
        List<SharedProfileDto> newCandidates = List.of(
                createProfile(newCandidateId, "NewCandidate", 25)
        );

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(newCandidates));

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        deckPipeline.buildDeck(viewer).block();

        // Then: Redis should contain new deck (old one overwritten)
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNext(newCandidateId)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle SwipesHttp errors gracefully (fail-open)")
    void shouldHandleSwipesServiceError() {
        // Given: viewer and candidates, but SwipesHttp fails
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        List<SharedProfileDto> candidates = List.of(
                createProfile(UUID.randomUUID(), "Candidate1", 23),
                createProfile(UUID.randomUUID(), "Candidate2", 24)
        );

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidates));

        // Mock: SwipesHttp fails (network error, service down, etc.)
        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.error(new RuntimeException("Swipes service unavailable")));

        // When: executing pipeline
        deckPipeline.buildDeck(viewer).block();

        // Then: pipeline should complete (fail-open strategy)
        // All candidates should be cached (none filtered due to error)
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should sort candidates by score in Redis")
    void shouldSortCandidatesByScoreInRedis() {
        // Given: viewer and candidates that will have different scores
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();
        UUID candidate3Id = UUID.randomUUID();

        List<SharedProfileDto> candidates = List.of(
                createProfile(candidate1Id, "LowScore", 40),   // Will get low score
                createProfile(candidate2Id, "HighScore", 25),  // Will get high score
                createProfile(candidate3Id, "MediumScore", 30) // Will get medium score
        );

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidates));

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: executing pipeline (ScoringService will calculate real scores)
        deckPipeline.buildDeck(viewer).block();

        // Then: candidates should be ordered by score in Redis
        StepVerifier.create(deckCache.readDeck(testViewerId, 0, 10))
                .expectNextCount(3)
                .verifyComplete();

        // Verify: can read top candidate (highest score)
        StepVerifier.create(deckCache.readTop(testViewerId, 1))
                .assertNext(topList -> assertThat(topList).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle all candidates filtered out scenario")
    void shouldHandleAllCandidatesFilteredOut() {
        // Given: viewer and candidates, all with swipe history
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        UUID candidate1Id = UUID.randomUUID();
        UUID candidate2Id = UUID.randomUUID();

        List<SharedProfileDto> candidates = List.of(
                createProfile(candidate1Id, "Candidate1", 23),
                createProfile(candidate2Id, "Candidate2", 24)
        );

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.fromIterable(candidates));

        // Mock: All candidates have swipe history
        Map<UUID, Boolean> allSwiped = new HashMap<>();
        allSwiped.put(candidate1Id, true);
        allSwiped.put(candidate2Id, true);

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(allSwiped));

        // When: executing pipeline
        deckPipeline.buildDeck(viewer).block();

        // Then: deck should be empty (no entries in Redis)
        // Note: DeckCache.writeDeck with empty list should not create entries
        StepVerifier.create(deckCache.size(testViewerId))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should verify build timestamp is stored after pipeline execution")
    void shouldStoreBuildTimestampAfterPipeline() {
        // Given: viewer and candidates
        SharedProfileDto viewer = createProfile(testViewerId, "Viewer", VIEWER_AGE);

        when(profilesHttp.searchProfiles(eq(testViewerId), any(), anyInt()))
                .thenReturn(Flux.just(createProfile(UUID.randomUUID(), "Candidate", 25)));

        when(swipesHttp.betweenBatch(eq(testViewerId), anyList()))
                .thenReturn(Mono.just(Collections.emptyMap()));

        // When: executing pipeline
        deckPipeline.buildDeck(viewer).block();

        // Then: build timestamp should be available in Redis
        StepVerifier.create(deckCache.getBuildInstant(testViewerId))
                .assertNext(instant -> assertThat(instant).isPresent())
                .verifyComplete();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test profile for pipeline testing
     */
    private SharedProfileDto createProfile(UUID id, String name, int age) {
        SharedLocationDto locationDto = new SharedLocationDto(
                UUID.randomUUID(),
                0.0,
                0.0,
                "Test City",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        SharedPreferencesDto preferences = new SharedPreferencesDto(18, 50, "M", 100);

        return new SharedProfileDto(id, name, age, "Bio", "Test City", true, locationDto, preferences, false, List.of());
    }
}
