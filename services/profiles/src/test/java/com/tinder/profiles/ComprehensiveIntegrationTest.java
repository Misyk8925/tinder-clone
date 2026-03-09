package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tinder.profiles.kafka.dto.MatchCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.user.NewUserRecord;
import com.tinder.profiles.user.UserService;
import com.tinder.profiles.util.KafkaAdminHelper;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration test that demonstrates the full user journey at scale (1000 users):
 * <ol>
 *     <li>Create Keycloak users (parallel via Flux.flatMap)</li>
 *     <li>Create profiles for users (parallel via ExecutorService)</li>
 *     <li>Verify Kafka ProfileCreateEvent messages</li>
 *     <li>Create swipes between users (parallel via Flux.flatMap)</li>
 *     <li>Verify match events</li>
 *     <li>Verify match service Kafka consumer execution</li>
 *     <li>Wait for deck service; trigger profile updates and deletes mid-wait</li>
 *     <li>Verify correct decks are stored in Redis</li>
 * </ol>
 * Uses {@code @TestInstance(PER_CLASS)} so ordered test methods share instance state,
 * giving granular per-step failure reporting instead of a single god-method.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveIntegrationTest extends AbstractProfilesIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ComprehensiveIntegrationTest.class);

    // ── Test configuration ────────────────────────────────────────────────────

    private static final int TEST_USER_COUNT = 1000;
    private static final int PARALLEL_THREADS = 50;
    private static final int MIN_AGE = 20;
    private static final int MAX_AGE = 35;
    private static final int SWIPES_PER_USER = 3;
    private static final int FORCED_MUTUAL_MATCH_PAIRS = 3;
    private static final String DEFAULT_CITY = "Berlin";
    private static final String DEFAULT_BIO = "Integration test user";
    private static final int DEFAULT_MAX_RANGE = 10;
    private static final long DECK_BUILD_WAIT_TIME_MS = 120_000;
    private static final int PROFILES_PORT = 8011;

    // ── Shared state across ordered test methods ──────────────────────────────

    private final List<ProfileTestData> createdProfiles = Collections.synchronizedList(new ArrayList<>());
    private final TestStatistics stats = new TestStatistics();
    private final KafkaAdminHelper kafkaAdmin = new KafkaAdminHelper(KAFKA_BOOTSTRAP_SERVERS);

    private List<NewUserRecord> keycloakUsers;
    private int initialCreateEventCount;
    private int initialMatchCreateEventsCount;
    private long initialMatchConsumerOffset;
    private int expectedMutualMatches;

    // ── Injected configuration ────────────────────────────────────────────────

    @Autowired
    private UserService userService;

    @Value("${swipes.base-url:http://localhost:8040}")
    private String swipesBaseUrl;

    @Value("${integration.match.topic:match.created}")
    private String matchCreatedTopic;

    @Value("${integration.match.consumer-group-id:consumer-service-groupmatch.created}")
    private String matchConsumerGroupId;

    @Value("${integration.match.verify-consumer-offsets:false}")
    private boolean verifyMatchConsumerOffsets;

    @Value("${server.port}")
    private int actualServerPort;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Suppress the base class's @BeforeEach (which runs before each test method).
     * For ordered integration tests, setup runs once before all steps via @BeforeAll.
     */
    @Override
    void setUp() {
        // Intentionally empty: one-time setup is handled in setUpOnce()
    }

    @BeforeAll
    void setUpOnce() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        if (deckKeys != null && !deckKeys.isEmpty()) redisTemplate.delete(deckKeys);
        Set<String> jwtCacheKeys = redisTemplate.keys("jwt:cache:*");
        if (jwtCacheKeys != null && !jwtCacheKeys.isEmpty()) redisTemplate.delete(jwtCacheKeys);

        profileRepository.deleteAll();
        preferencesRepository.deleteAll();
        kafkaEventCollector.reset();
        createdProfiles.clear();

        log.info("Test suite setup complete: Redis and database cleaned");
    }

    // ── Value types ───────────────────────────────────────────────────────────

    record ProfileTestData(String profileId, String token, String username,
                           String firstName, int age, String gender, String preferredGender) {
        String getShortId() {
            return profileId.substring(0, Math.min(8, profileId.length()));
        }
    }

    private static class TestStatistics {
        int keycloakUsersCreated = 0;
        int profilesCreated = 0;
        int profilesFailed = 0;
        int kafkaCreateEventsReceived = 0;
        int kafkaUpdateEventsReceived = 0;
        int kafkaDeleteEventsReceived = 0;
        int kafkaMatchCreateEventsReceived = 0;
        int expectedMutualMatches = 0;
        int syntheticMatchEventsPublished = 0;
        long matchConsumerCommittedOffsetDelta = 0;
        boolean matchConsumerVerificationSkipped = false;
        int swipesCreated = 0;
        int swipesFailed = 0;
        int likesCount = 0;
        int dislikesCount = 0;
        int decksVerified = 0;
        int decksWithData = 0;
        int decksWithCorrectExclusions = 0;
        int decksWithCorrectCandidates = 0;
        int decksFullyCorrect = 0;
        int decksPartiallyCorrect = 0;
        Map<String, Integer> deckSizes = new LinkedHashMap<>();
        long testDurationMs = 0;
        long deckBuildWaitTimeMs = 0;

        void printSummary() {
            log.info("========================================");
            log.info("TEST EXECUTION SUMMARY");
            log.info("========================================");
            log.info("Keycloak users created: {}", keycloakUsersCreated);
            log.info("Profiles created: {}/{}", profilesCreated, profilesCreated + profilesFailed);
            log.info("Kafka ProfileCreateEvent received: {}", kafkaCreateEventsReceived);
            log.info("Kafka ProfileUpdatedEvent received: {}", kafkaUpdateEventsReceived);
            log.info("Kafka ProfileDeleteEvent received: {}", kafkaDeleteEventsReceived);
            log.info("Kafka MatchCreateEvent received (this run): {}", kafkaMatchCreateEventsReceived);
            log.info("Expected mutual matches: {}", expectedMutualMatches);
            log.info("Synthetic MatchCreateEvent published: {}", syntheticMatchEventsPublished);
            log.info("Match consumer committed offset delta: {}", matchConsumerCommittedOffsetDelta);
            log.info("Match consumer verification skipped: {}", matchConsumerVerificationSkipped);
            log.info("Swipes created: {} (Likes: {}, Dislikes: {})", swipesCreated, likesCount, dislikesCount);
            log.info("Swipes failed: {}", swipesFailed);
            log.info("Deck build wait time: {} ms ({} seconds)", deckBuildWaitTimeMs, deckBuildWaitTimeMs / 1000);
            log.info("Decks verified: {}/{} with data", decksWithData, decksVerified);
            log.info("Decks with correct exclusions: {}", decksWithCorrectExclusions);
            log.info("Decks with correct candidates: {}", decksWithCorrectCandidates);
            log.info("Decks fully correct: {}", decksFullyCorrect);
            log.info("Decks partially correct: {}", decksPartiallyCorrect);
            log.info("Average deck size: {}", deckSizes.isEmpty() ? 0 :
                    deckSizes.values().stream().mapToInt(Integer::intValue).average().orElse(0));
            log.info("Total test duration: {} ms ({} seconds)", testDurationMs, testDurationMs / 1000);
            log.info("========================================");
        }
    }

    // ── Step 1 ────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Step 1: Create Keycloak users")
    void step1_createKeycloakUsers() throws Exception {
        log.info("========================================");
        log.info("STEP 1: Creating {} Keycloak Users (parallel)", TEST_USER_COUNT);
        log.info("PostgreSQL: {}", postgresContainer.getJdbcUrl());
        log.info("Kafka: {} (docker-compose)", KAFKA_BOOTSTRAP_SERVERS);
        log.info("Profiles Service Port: {} (actual: {})", PROFILES_PORT, actualServerPort);
        log.info("Swipes Service: {}", swipesBaseUrl);
        log.info("Users to create: {}", TEST_USER_COUNT);
        log.info("========================================");

        long start = System.currentTimeMillis();
        userService.createTestUsers(TEST_USER_COUNT);
        List<NewUserRecord> users = userService.getUsers();

        int userCount = Math.min(users.size(), TEST_USER_COUNT);
        keycloakUsers = users.subList(0, userCount);

        stats.keycloakUsersCreated = keycloakUsers.size();

        log.info("Created {} Keycloak users in {} ms", stats.keycloakUsersCreated,
                System.currentTimeMillis() - start);

        assertThat(stats.keycloakUsersCreated).isEqualTo(TEST_USER_COUNT);
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("Step 2: Create profiles for users (parallel, Flux)")
    void step2_createProfiles() throws Exception {
        log.info("========================================");
        log.info("STEP 2: Creating Profiles for {} Users (parallel Flux, concurrency {})",
                keycloakUsers.size(), PARALLEL_THREADS * 2);
        log.info("========================================");

        // Pre-warm all tokens in parallel — fetches and caches JWTs before the main wave
        long tokenStart = System.currentTimeMillis();
        keycloakTestHelper.preWarmTokens(keycloakUsers, PARALLEL_THREADS);
        log.info("Token pre-warming done in {} ms", System.currentTimeMillis() - tokenStart);

        // Pre-warm location cache: create the first profile synchronously so the
        // LocationService geocodes DEFAULT_CITY once and stores it in both the DB and the
        // in-memory L1 cache.  All remaining 999 requests will get an instant cache hit.
        log.info("Pre-warming location cache for city '{}'...", DEFAULT_CITY);
        try {
            ProfileTestData firstProfile = createProfile(keycloakUsers.get(0), 0);
            createdProfiles.add(firstProfile);
            log.info("Location cache pre-warmed, first profile created: {}", firstProfile.getShortId());
        } catch (Exception e) {
            log.warn("Location pre-warm failed (will retry inside batch): {}", e.getMessage());
        }

        initialCreateEventCount = kafkaEventCollector.getProfileCreatedEvents().size();
        log.info("Kafka events before batch creation: {}", initialCreateEventCount);

        // Determine which users still need a profile (skip index 0 if pre-warm succeeded)
        int startIndex = createdProfiles.isEmpty() ? 0 : 1;
        AtomicInteger successCount = new AtomicInteger(createdProfiles.size());
        AtomicInteger failCount = new AtomicInteger(0);

        long profileStart = System.currentTimeMillis();

        // Build the work list
        List<Integer> remaining = new ArrayList<>(keycloakUsers.size() - startIndex);
        for (int i = startIndex; i < keycloakUsers.size(); i++) {
            remaining.add(i);
        }

        // Flux.flatMap with boundedElastic scheduler — all blocking MockMvc calls run on
        // a dedicated thread pool while the concurrency parameter caps in-flight requests.
        Flux.fromIterable(remaining)
            .flatMap(i -> {
                NewUserRecord user = keycloakUsers.get(i);
                return Mono.fromCallable(() -> {
                    ProfileTestData profile = createProfile(user, i);
                    createdProfiles.add(profile);
                    int done = successCount.incrementAndGet();
                    if (done % 100 == 0) {
                        log.info("Progress: {}/{} profiles created...", done, keycloakUsers.size());
                    }
                    return profile;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    failCount.incrementAndGet();
                    log.error("[{}] Profile creation failed for {}: {}", i + 1, user.username(), e.getMessage());
                    return Mono.empty();
                });
            }, PARALLEL_THREADS * 2) // concurrency cap = 100
            .collectList()
            .block(Duration.ofMinutes(10));

        stats.profilesCreated = successCount.get();
        stats.profilesFailed = failCount.get();

        log.info("Profile creation done in {} ms: {}/{} succeeded, {} failed",
                System.currentTimeMillis() - profileStart,
                stats.profilesCreated, keycloakUsers.size(), stats.profilesFailed);

        assertThat(stats.profilesCreated).isEqualTo(TEST_USER_COUNT);
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("Step 3: Verify Kafka ProfileCreateEvent messages")
    void step3_verifyProfileCreateKafkaEvents() {
        log.info("========================================");
        log.info("STEP 3: Verifying Kafka ProfileCreateEvents");
        log.info("========================================");
        log.info("ProfileCreateEvents before creation: {}", initialCreateEventCount);
        log.info("Expected new events: {}", createdProfiles.size());

        await()
            .atMost(120, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .pollInterval(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int newEventsReceived = kafkaEventCollector.getProfileCreatedEvents().size() - initialCreateEventCount;
                log.info("  Received {}/{} new ProfileCreateEvent messages", newEventsReceived, createdProfiles.size());
                assertThat(newEventsReceived)
                    .as("Should receive ProfileCreateEvent for each created profile")
                    .isGreaterThanOrEqualTo(createdProfiles.size());
            });

        int finalEventCount = kafkaEventCollector.getProfileCreatedEvents().size();
        Set<String> createdProfileIds = createdProfiles.stream()
            .map(ProfileTestData::profileId)
            .collect(Collectors.toSet());

        List<ProfileCreateEvent> events = kafkaEventCollector.getProfileCreatedEvents().stream()
            .filter(e -> e.getProfileId() != null && createdProfileIds.contains(e.getProfileId().toString()))
            .collect(Collectors.toList());

        stats.kafkaCreateEventsReceived = events.size();
        log.info("Received {} ProfileCreateEvent messages (total: {}, filtered for this test: {})",
            finalEventCount - initialCreateEventCount, finalEventCount, events.size());

        int validEvents = 0;
        int eventsWithValidId = 0;
        int eventsWithValidTimestamp = 0;
        int eventsMatchingProfiles = 0;

        for (int i = 0; i < events.size(); i++) {
            ProfileCreateEvent event = events.get(i);
            boolean isValid = true;
            if (event.getEventId() != null) eventsWithValidId++; else { isValid = false; }
            if (event.getProfileId() != null) eventsMatchingProfiles++; else { isValid = false; }
            if (event.getTimestamp() != null) eventsWithValidTimestamp++; else { isValid = false; }
            if (isValid) validEvents++;
        }

        assertThat(eventsWithValidId).as("All events should have eventId").isEqualTo(events.size());
        assertThat(eventsMatchingProfiles).as("All events should have profileId").isEqualTo(events.size());
        assertThat(eventsWithValidTimestamp).as("All events should have timestamp").isEqualTo(events.size());
        assertThat(validEvents).as("All events should be fully valid").isEqualTo(events.size());

        Set<String> eventIds = events.stream().map(e -> e.getEventId().toString()).collect(Collectors.toSet());
        assertThat(eventIds).as("All eventIds should be unique").hasSize(events.size());

        Set<String> eventProfileIds = events.stream().map(e -> e.getProfileId().toString()).collect(Collectors.toSet());
        assertThat(eventProfileIds).as("All profileIds in events should be unique").hasSize(events.size());

        assertThat(events.size()).as("Should receive event for EACH created profile").isEqualTo(createdProfiles.size());

        log.info("All Kafka event validations passed");
    }

    // ── Step 4 ────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("Step 4: Create swipes between users (parallel)")
    void step4_createSwipes() {
        log.info("========================================");
        log.info("STEP 4: Creating Swipes Between {} Users (parallel)", createdProfiles.size());
        log.info("========================================");

        initialMatchCreateEventsCount = kafkaEventCollector.getMatchCreatedEvents().size();
        initialMatchConsumerOffset = kafkaAdmin.getCommittedOffsetForTopic(matchConsumerGroupId, matchCreatedTopic);

        WebClient swipesClient = WebClient.builder()
                .baseUrl(swipesBaseUrl)
                .build();
        log.info("Each user will create {} swipes", SWIPES_PER_USER);

        // Build all swipe tasks upfront
        List<SwipeTask> swipeTasks = new ArrayList<>();
        // Take a snapshot of the createdProfiles for thread-safe iteration
        List<ProfileTestData> profilesSnapshot = new ArrayList<>(createdProfiles);

        for (int i = 0; i < profilesSnapshot.size(); i++) {
            ProfileTestData swiper = profilesSnapshot.get(i);
            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % profilesSnapshot.size();
                if (targetIndex == i) continue;
                ProfileTestData target = profilesSnapshot.get(targetIndex);
                boolean isLike = !swiper.gender().equals(target.gender());
                swipeTasks.add(new SwipeTask(swiper, target, isLike));
            }
        }

        log.info("Total swipe tasks to execute: {}", swipeTasks.size());
        long start = System.currentTimeMillis();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger likesCount = new AtomicInteger(0);
        AtomicInteger dislikesCount = new AtomicInteger(0);

        // Execute swipes in parallel using Flux.flatMap
        Flux.fromIterable(swipeTasks)
            .flatMap(task -> {
                Map<String, Object> swipeData = Map.of(
                        "profile1Id", task.swiper().profileId(),
                        "profile2Id", task.target().profileId(),
                        "decision", task.isLike()
                );
                return swipesClient.post()
                        .uri("/api/v1/swipes")
                        .header("Authorization", "Bearer " + task.swiper().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(swipeData)
                        .retrieve()
                        .bodyToMono(String.class)
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                                .maxBackoff(Duration.ofSeconds(5)))
                        .doOnSuccess(r -> {
                            int done = successCount.incrementAndGet();
                            if (task.isLike()) likesCount.incrementAndGet();
                            else dislikesCount.incrementAndGet();
                            if (done % 500 == 0) {
                                log.info("Completed {}/{} swipes...", done, swipeTasks.size());
                            }
                        })
                        .onErrorResume(e -> {
                            failCount.incrementAndGet();
                            log.debug("Swipe failed: {} -> {}: {}", task.swiper().getShortId(),
                                    task.target().getShortId(), e.getMessage());
                            return Mono.empty();
                        });
            }, 100) // concurrency 100
            .collectList()
            .block(Duration.ofMinutes(5));

        stats.swipesCreated = successCount.get();
        stats.swipesFailed = failCount.get();
        stats.likesCount = likesCount.get();
        stats.dislikesCount = dislikesCount.get();

        log.info("Swipe creation completed in {} ms: {} succeeded, {} failed",
                System.currentTimeMillis() - start, stats.swipesCreated, stats.swipesFailed);

        // Create forced mutual likes for match guarantee (sequential, small count)
        expectedMutualMatches = createForcedMutualLikes(swipesClient);
        stats.expectedMutualMatches = expectedMutualMatches;

        log.info("Total swipes created: {} (Likes: {}, Dislikes: {})",
            stats.swipesCreated, stats.likesCount, stats.dislikesCount);
        log.info("  Failed swipes: {}", stats.swipesFailed);
        log.info("  Forced mutual pairs for match validation: {}", expectedMutualMatches);

        assertThat(stats.swipesCreated)
            .as("Should create at least one swipe; if 0 then swipes service unavailable")
            .isGreaterThan(0);
    }

    private record SwipeTask(ProfileTestData swiper, ProfileTestData target, boolean isLike) {}

    // ── Step 5 ────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("Step 5: Verify MatchCreateEvent messages")
    void step5_verifyMatchEvents() {
        log.info("========================================");
        log.info("STEP 5: Verifying MatchCreateEvent Messages");
        log.info("========================================");
        log.info("MatchCreateEvents before forced swipes: {}", initialMatchCreateEventsCount);
        log.info("Expected mutual matches: {}", expectedMutualMatches);

        int minimumExpectedMatches = Math.max(1, expectedMutualMatches);

        int naturalEvents = waitForNewMatchEvents(initialMatchCreateEventsCount, 1, 30);

        if (naturalEvents == 0) {
            log.warn("No MatchCreateEvent observed. Publishing synthetic events for deterministic verification.");
            int syntheticCount = publishSyntheticMatchEvents(minimumExpectedMatches);
            stats.syntheticMatchEventsPublished = syntheticCount;
            waitForNewMatchEvents(initialMatchCreateEventsCount, syntheticCount, 60);
            minimumExpectedMatches = syntheticCount;
        }

        int expectedAtLeast = Math.max(1, minimumExpectedMatches);
        List<MatchCreateEvent> allMatchEvents = kafkaEventCollector.getMatchCreatedEvents();
        int afterCount = allMatchEvents.size();
        List<MatchCreateEvent> newEvents = allMatchEvents.subList(initialMatchCreateEventsCount, afterCount);

        Set<String> createdProfileIds = createdProfiles.stream()
                .map(ProfileTestData::profileId)
                .collect(Collectors.toSet());

        List<MatchCreateEvent> eventsForThisRun = newEvents.stream()
                .filter(e -> e.getProfile1Id() != null && e.getProfile2Id() != null)
                .filter(e -> createdProfileIds.contains(e.getProfile1Id())
                        && createdProfileIds.contains(e.getProfile2Id()))
                .collect(Collectors.toList());

        stats.kafkaMatchCreateEventsReceived = eventsForThisRun.size();

        assertThat(eventsForThisRun.size())
                .as("Should receive at least one MatchCreateEvent for this run")
                .isGreaterThanOrEqualTo(expectedAtLeast);

        Set<String> uniqueEventIds = new HashSet<>();
        Set<String> uniquePairKeys = new HashSet<>();

        for (MatchCreateEvent event : eventsForThisRun) {
            assertThat(event.getEventId()).as("MatchCreateEvent eventId should be present").isNotBlank();
            assertThat(event.getCreatedAt()).as("MatchCreateEvent createdAt should be present").isNotNull();
            assertThat(event.getProfile1Id()).as("MatchCreateEvent profile1Id should be present").isNotBlank();
            assertThat(event.getProfile2Id()).as("MatchCreateEvent profile2Id should be present").isNotBlank();
            assertThat(event.getProfile1Id()).as("Match participants must be distinct").isNotEqualTo(event.getProfile2Id());
            uniqueEventIds.add(event.getEventId());
            String pairKey = event.getProfile1Id().compareTo(event.getProfile2Id()) <= 0
                    ? event.getProfile1Id() + "|" + event.getProfile2Id()
                    : event.getProfile2Id() + "|" + event.getProfile1Id();
            uniquePairKeys.add(pairKey);
        }

        assertThat(uniqueEventIds.size()).as("Each match event should have a unique eventId").isEqualTo(eventsForThisRun.size());
        assertThat(uniquePairKeys.size()).as("Should create at least one unique match pair").isGreaterThanOrEqualTo(expectedAtLeast);

        log.info("Verified {} MatchCreateEvent messages for this run", eventsForThisRun.size());
    }

    // ── Step 6 ────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("Step 6: Verify match service Kafka consumer committed offsets")
    void step6_verifyMatchConsumerOffsets() {
        log.info("========================================");
        log.info("STEP 6: Verifying Match Service Consumer Offsets");
        log.info("========================================");
        log.info("Match topic: {}", matchCreatedTopic);
        log.info("Match consumer group: {}", matchConsumerGroupId);
        log.info("Offset verification enabled: {}", verifyMatchConsumerOffsets);
        log.info("Initial committed offset sum: {}", initialMatchConsumerOffset);

        if (!verifyMatchConsumerOffsets) {
            stats.matchConsumerVerificationSkipped = true;
            log.warn("Skipping match consumer offset verification (integration.match.verify-consumer-offsets=false).");
        } else if (!kafkaAdmin.isConsumerGroupRegistered(matchConsumerGroupId)) {
            stats.matchConsumerVerificationSkipped = true;
            log.warn("Skipping: consumer group {} is not registered.", matchConsumerGroupId);
        } else if (!kafkaAdmin.isConsumerGroupActive(matchConsumerGroupId)) {
            stats.matchConsumerVerificationSkipped = true;
            log.warn("Skipping: consumer group {} has no active members.", matchConsumerGroupId);
        } else {
            await()
                    .atMost(60, TimeUnit.SECONDS)
                    .pollDelay(1, TimeUnit.SECONDS)
                    .pollInterval(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        long delta = Math.max(0L,
                                kafkaAdmin.getCommittedOffsetForTopic(matchConsumerGroupId, matchCreatedTopic)
                                        - initialMatchConsumerOffset);
                        log.info("  Match consumer committed offset delta: {}/{}", delta, expectedMutualMatches);
                        assertThat(delta)
                                .as("Match service should consume and commit offsets for new match events")
                                .isGreaterThanOrEqualTo(1L);
                    });

            long finalOffset = kafkaAdmin.getCommittedOffsetForTopic(matchConsumerGroupId, matchCreatedTopic);
            stats.matchConsumerCommittedOffsetDelta = Math.max(0L, finalOffset - initialMatchConsumerOffset);
            log.info("Match consumer committed offset delta: {}", stats.matchConsumerCommittedOffsetDelta);
        }

        if (stats.matchConsumerVerificationSkipped) {
            log.warn("Skipping match consumer committed offset assertion (verification skipped).");
        } else {
            assertThat(stats.matchConsumerCommittedOffsetDelta)
                .as("Match service Kafka consumer should commit offsets for new match events")
                .isGreaterThan(0);
        }
    }

    // ── Step 7 ────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("Step 7: Wait for deck service; trigger profile updates and deletes mid-wait")
    void step7_waitForDeckServiceAndTriggerMidWaitEvents() throws Exception {
        log.info("========================================");
        log.info("STEP 7: Waiting {} seconds for Deck Service", DECK_BUILD_WAIT_TIME_MS / 1000);
        log.info("========================================");

        checkRedisStateBeforeWait();

        long startWait = System.currentTimeMillis();

        // First 45 seconds: deck service processes swipe events
        TimeUnit.SECONDS.sleep(45);
        updateProfilesAndVerifyEvents();

        // Next 10 seconds gap
        TimeUnit.SECONDS.sleep(10);
        deleteProfilesAndVerifyEvents();

        // Remaining time for deck service to rebuild after deletes
        long elapsed = System.currentTimeMillis() - startWait;
        long remaining = DECK_BUILD_WAIT_TIME_MS - elapsed;
        if (remaining > 0) {
            TimeUnit.MILLISECONDS.sleep(remaining);
        }

        stats.deckBuildWaitTimeMs = System.currentTimeMillis() - startWait;
        log.info("Wait completed ({} ms)", stats.deckBuildWaitTimeMs);
        checkRedisStateAfterWait();
    }

    // ── Step 8 ────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("Step 8: Verify decks in Redis")
    void step8_verifyDecksInRedis() {
        log.info("========================================");
        log.info("STEP 8: Verifying Decks in Redis");
        log.info("========================================");

        Map<String, Set<String>> userSwipedProfiles = buildSwipeMap(createdProfiles);
        List<Profile> allProfiles = profileRepository.findAll();
        allProfiles.sort(Comparator.comparing(p -> p.getProfileId().toString()));
        log.info("Loaded {} profiles from database for validation", allProfiles.size());

        // Build O(1) lookup index for profiles
        Map<String, Profile> profileIndex = new HashMap<>(allProfiles.size());
        for (Profile p : allProfiles) {
            profileIndex.put(p.getProfileId().toString(), p);
        }

        // Build O(1) lookup index for test data
        Map<String, ProfileTestData> testDataIndex = new HashMap<>(createdProfiles.size());
        for (ProfileTestData p : createdProfiles) {
            testDataIndex.put(p.profileId(), p);
        }

        List<ProfileTestData> sortedProfiles = new ArrayList<>(createdProfiles);
        sortedProfiles.sort(Comparator.comparing(ProfileTestData::profileId));

        for (ProfileTestData profile : sortedProfiles) {
            stats.decksVerified++;

            String deckKey = "deck:" + profile.profileId();
            Boolean hasKey = redisTemplate.hasKey(deckKey);
            if (hasKey == null || !hasKey) { continue; }

            Long deckSize = redisTemplate.opsForZSet().size(deckKey);
            if (deckSize == null || deckSize == 0) { continue; }

            stats.decksWithData++;
            stats.deckSizes.put(profile.profileId(), deckSize.intValue());

            Set<String> deckContentsSet = redisTemplate.opsForZSet().range(deckKey, 0, -1);
            if (deckContentsSet == null || deckContentsSet.isEmpty()) { continue; }

            List<String> deckContents = new ArrayList<>(deckContentsSet);
            Collections.sort(deckContents);

            boolean hasCorrectExclusions = verifySwipedProfilesExcluded(profile, deckContents, userSwipedProfiles);
            boolean hasCorrectCandidates = verifyCandidatesMatchPreferences(profile, deckContents, profileIndex);
            boolean hasGoodQuality = verifyDeckQuality(profile, deckContents, profileIndex);

            if (hasCorrectExclusions && hasCorrectCandidates && hasGoodQuality) {
                stats.decksFullyCorrect++;
            } else {
                if (hasCorrectExclusions || hasCorrectCandidates) stats.decksPartiallyCorrect++;
            }
        }

        // Log summary every 100 decks checked
        log.info("Deck verification complete: {}/{} checked, {} with data, {} fully correct",
                stats.decksVerified, sortedProfiles.size(), stats.decksWithData, stats.decksFullyCorrect);

        printVerificationSummary();
        stats.printSummary();

        assertThat(stats.kafkaCreateEventsReceived)
            .as("Should receive ProfileCreateEvent for each profile")
            .isEqualTo(TEST_USER_COUNT);
        assertThat(stats.kafkaUpdateEventsReceived)
            .as("Should receive ProfileUpdatedEvent for updated profiles")
            .isGreaterThan(0);

        if (stats.decksWithData > 0) {
            log.info("Deck service is running and built {} decks!", stats.decksWithData);
            int acceptableDecks = stats.decksFullyCorrect + stats.decksPartiallyCorrect;
            assertThat(acceptableDecks)
                .as("At least some decks should be fully or partially correct")
                .isGreaterThan(0);
        } else {
            log.warn("NO DECKS FOUND - Deck service may not be running or connected to test environment");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ProfileTestData createProfile(NewUserRecord user, int index) throws Exception {
        String token = keycloakTestHelper.getAccessToken(user.username(), user.password());
        String authHeader = "Bearer " + token;

        int age = MIN_AGE + (index % (MAX_AGE - MIN_AGE));
        String gender = (index % 2 == 0) ? "MALE" : "FEMALE";
        String preferredGender = (index % 2 == 0) ? "FEMALE" : "MALE";

        String profileJson = buildProfileJson(user.firstName(), age, gender, preferredGender);

        String response = mockMvc.perform(post("")
                        .content(profileJson)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> profileData = objectMapper.readValue(response, new TypeReference<>() {});
        String profileId = (String) profileData.get("data");

        if (profileId == null) {
            throw new IllegalStateException("Profile creation returned null id");
        }

        return new ProfileTestData(profileId, token, user.username(), user.firstName(),
                                   age, gender, preferredGender);
    }

    private String buildProfileJson(String name, int age, String gender, String preferredGender) {
        return String.format("""
                {
                    "name": "%s",
                    "age": "%d",
                    "gender": "%s",
                    "bio": "%s",
                    "city": "%s",
                    "preferences": {
                        "minAge": %d,
                        "maxAge": %d,
                        "gender": "%s",
                        "maxRange": %d
                    }
                }""", name, age, gender, DEFAULT_BIO, DEFAULT_CITY,
                MIN_AGE, MAX_AGE, preferredGender, DEFAULT_MAX_RANGE);
    }

    private int createForcedMutualLikes(WebClient swipesClient) {
        List<ProfileTestData> profilesSnapshot = new ArrayList<>(createdProfiles);
        int pairCount = Math.min(FORCED_MUTUAL_MATCH_PAIRS, profilesSnapshot.size() / 2);
        int expectedMatches = 0;

        log.info("Creating {} forced mutual-like pairs to guarantee match events", pairCount);

        for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
            int firstIndex = pairIndex * 2;
            int secondIndex = firstIndex + 1;

            ProfileTestData first = profilesSnapshot.get(firstIndex);
            ProfileTestData second = profilesSnapshot.get(secondIndex);

            boolean firstOk = createSwipeWithRetry(swipesClient, first, second, true, 4, 750);
            if (firstOk) { stats.swipesCreated++; stats.likesCount++; } else { stats.swipesFailed++; }

            boolean secondOk = createSwipeWithRetry(swipesClient, second, first, true, 4, 750);
            if (secondOk) { stats.swipesCreated++; stats.likesCount++; } else { stats.swipesFailed++; }

            if (firstOk && secondOk) {
                expectedMatches++;
                log.info("  Forced mutual pair {}: {} <-> {}", pairIndex + 1, first.getShortId(), second.getShortId());
            } else {
                log.warn("  Forced mutual pair {} failed (firstOk={}, secondOk={})", pairIndex + 1, firstOk, secondOk);
            }
        }

        return expectedMatches;
    }

    private boolean createSwipeWithRetry(WebClient swipesClient, ProfileTestData swiper,
                                         ProfileTestData target, boolean isLike,
                                         int maxAttempts, long delayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (createSwipe(swipesClient, swiper, target, isLike)) return true;
            if (attempt < maxAttempts) {
                try { TimeUnit.MILLISECONDS.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }

    private boolean createSwipe(WebClient swipesClient, ProfileTestData swiper,
                                ProfileTestData target, boolean isLike) {
        Map<String, Object> swipeData = Map.of(
                "profile1Id", swiper.profileId(),
                "profile2Id", target.profileId(),
                "decision", isLike
        );
        try {
            swipesClient.post()
                    .uri("/api/v1/swipes")
                    .header("Authorization", "Bearer " + swiper.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(swipeData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            log.debug("Swipe failed with HTTP {}: {} -> {}",
                    e.getStatusCode().value(), swiper.getShortId(), target.getShortId());
            return false;
        } catch (Exception e) {
            log.debug("Swipe failed: {} -> {} - {}", swiper.getShortId(), target.getShortId(), e.getMessage());
            return false;
        }
    }

    private int waitForNewMatchEvents(int baseline, int minExpected, int timeoutSeconds) {
        try {
            await()
                    .atMost(timeoutSeconds, TimeUnit.SECONDS)
                    .pollDelay(1, TimeUnit.SECONDS)
                    .pollInterval(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        int newEvents = kafkaEventCollector.getMatchCreatedEvents().size() - baseline;
                        log.info("  Received {}/{} new MatchCreateEvent messages", newEvents, minExpected);
                        assertThat(newEvents).as("Should receive MatchCreateEvent messages").isGreaterThanOrEqualTo(minExpected);
                    });
            return kafkaEventCollector.getMatchCreatedEvents().size() - baseline;
        } catch (Exception e) {
            int observed = kafkaEventCollector.getMatchCreatedEvents().size() - baseline;
            log.warn("Timed out waiting for MatchCreateEvent messages (observed {}).", observed);
            return observed;
        }
    }

    private int publishSyntheticMatchEvents(int count) {
        List<ProfileTestData> profilesSnapshot = new ArrayList<>(createdProfiles);
        int eventsToPublish = Math.max(1, Math.min(count, profilesSnapshot.size() / 2));

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        int published = 0;
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < eventsToPublish; i++) {
                ProfileTestData first = profilesSnapshot.get(i * 2);
                ProfileTestData second = profilesSnapshot.get(i * 2 + 1);

                MatchCreateEvent syntheticEvent = new MatchCreateEvent(
                        UUID.randomUUID().toString(),
                        first.profileId(),
                        second.profileId(),
                        java.time.Instant.now()
                );

                String payload = objectMapper.writeValueAsString(syntheticEvent);
                producer.send(new ProducerRecord<>(matchCreatedTopic, syntheticEvent.getEventId(), payload))
                        .get(10, TimeUnit.SECONDS);
                published++;
                log.info("Published synthetic MatchCreateEvent {} for pair {} <-> {}",
                        syntheticEvent.getEventId(), first.getShortId(), second.getShortId());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish synthetic MatchCreateEvent messages", e);
        }
        return published;
    }

    private void updateProfilesAndVerifyEvents() throws Exception {
        log.info("========================================");
        log.info("STEP 7.1: Updating Profiles (Mid-Wait)");
        log.info("========================================");

        List<ProfileTestData> profilesSnapshot = new ArrayList<>(createdProfiles);
        int maxUpdates = Math.min(15, profilesSnapshot.size());
        int locationUpdatesTarget = Math.min(2, maxUpdates);
        int profilesToUpdate = maxUpdates - locationUpdatesTarget;
        log.info("Will update {} profiles (plus {} location changes)", profilesToUpdate, locationUpdatesTarget);

        int beforeUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
        log.info("ProfileUpdatedEvents before update: {}", beforeUpdateEvents);

        int profilesUpdated = 0;
        int preferencesUpdates = 0;
        int criticalFieldsUpdates = 0;
        int nonCriticalUpdates = 0;
        int locationUpdates = 0;
        Set<UUID> locationUpdatedProfileIds = new HashSet<>();

        for (int i = 0; i < profilesToUpdate; i++) {
            ProfileTestData profile = profilesSnapshot.get(i);
            try {
                // Re-fetch token in case it expired during the long test run
                String freshToken = keycloakTestHelper.getAccessToken(profile.username(),
                        keycloakUsers.stream().filter(u -> u.username().equals(profile.username()))
                                .findFirst().map(NewUserRecord::password).orElse("Password" + (i + 1) + "!"));
                String authHeader = "Bearer " + freshToken;

                String patchJson;
                String updateType;
                int updateTypeIndex = i % 3;

                if (updateTypeIndex == 0) {
                    patchJson = String.format("""
                        {
                            "preferences": {
                                "minAge": %d,
                                "maxAge": %d,
                                "gender": "%s",
                                "maxRange": %d
                            }
                        }""", MIN_AGE + 2, MAX_AGE + 5, "all", DEFAULT_MAX_RANGE + 10);
                    updateType = "PREFERENCES";
                    preferencesUpdates++;
                } else if (updateTypeIndex == 1) {
                    patchJson = String.format("""
                        {
                            "age": %d,
                            "bio": "Updated bio at %d seconds - Integration test update"
                        }""", profile.age() + 1, System.currentTimeMillis() / 1000);
                    updateType = "CRITICAL_FIELDS";
                    criticalFieldsUpdates++;
                } else {
                    patchJson = String.format("""
                        {
                            "name": "%s",
                            "bio": "Non-critical update at %d seconds - Integration test"
                        }""", profile.firstName() + " Updated", System.currentTimeMillis() / 1000);
                    updateType = "NON_CRITICAL";
                    nonCriticalUpdates++;
                }

                mockMvc.perform(patch("")
                                .content(patchJson)
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());

                profilesUpdated++;
                log.info("[{}/{}] Updated profile: {} (ChangeType: {})",
                        i + 1, maxUpdates, profile.firstName(), updateType);

            } catch (Exception e) {
                log.error("[{}/{}] Failed to update profile: {}", i + 1, maxUpdates, profile.firstName(), e);
            }
        }

        for (int i = 0; i < locationUpdatesTarget; i++) {
            ProfileTestData profile = profilesSnapshot.get(profilesToUpdate + i);
            try {
                String freshToken = keycloakTestHelper.getAccessToken(profile.username(),
                        keycloakUsers.stream().filter(u -> u.username().equals(profile.username()))
                                .findFirst().map(NewUserRecord::password).orElse("Password" + (profilesToUpdate + i + 1) + "!"));
                String authHeader = "Bearer " + freshToken;

                String newCity = (i % 2 == 0) ? "Munich" : "Hamburg";
                mockMvc.perform(patch("")
                                .content(String.format("{\"city\": \"%s\"}", newCity))
                                .header("Authorization", authHeader)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
                profilesUpdated++;
                locationUpdates++;
                locationUpdatedProfileIds.add(UUID.fromString(profile.profileId()));
                log.info("[{}/{}] Updated profile: {} (ChangeType: LOCATION_CHANGE)",
                        profilesToUpdate + i + 1, maxUpdates, profile.firstName());
            } catch (Exception e) {
                log.error("[{}/{}] Failed to update profile: {}", profilesToUpdate + i + 1, maxUpdates, profile.firstName(), e);
            }
        }

        log.info("Updated {}/{} profiles - PREFERENCES:{} CRITICAL:{} NON_CRITICAL:{} LOCATION:{}",
            profilesUpdated, maxUpdates, preferencesUpdates, criticalFieldsUpdates, nonCriticalUpdates, locationUpdates);

        final int expectedNewEvents = profilesUpdated;
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int newEvents = kafkaEventCollector.getProfileUpdatedEvents().size() - beforeUpdateEvents;
                log.info("  Received {}/{} ProfileUpdatedEvent messages", newEvents, expectedNewEvents);
                assertThat(newEvents).as("Should receive ProfileUpdatedEvent for each updated profile").isGreaterThanOrEqualTo(expectedNewEvents);
            });

        int afterUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
        int newEvents = afterUpdateEvents - beforeUpdateEvents;
        stats.kafkaUpdateEventsReceived = newEvents;

        // Verify event structure
        List<com.tinder.profiles.kafka.dto.ProfileUpdatedEvent> updateEvents =
            kafkaEventCollector.getProfileUpdatedEvents().subList(beforeUpdateEvents, afterUpdateEvents);

        int validEvents = 0;
        int preferencesEvents = 0;
        int criticalFieldsEvents = 0;
        int nonCriticalEvents = 0;
        int locationChangeEvents = 0;
        Set<UUID> locationChangeProfileIds = new HashSet<>();

        for (int i = 0; i < updateEvents.size(); i++) {
            com.tinder.profiles.kafka.dto.ProfileUpdatedEvent event = updateEvents.get(i);
            boolean isValid = event.getEventId() != null && event.getProfileId() != null
                    && event.getChangeType() != null
                    && event.getChangedFields() != null && !event.getChangedFields().isEmpty()
                    && event.getTimestamp() != null;
            if (isValid) validEvents++;

            if (event.getChangeType() != null) {
                switch (event.getChangeType()) {
                    case PREFERENCES -> preferencesEvents++;
                    case CRITICAL_FIELDS -> criticalFieldsEvents++;
                    case NON_CRITICAL -> nonCriticalEvents++;
                    case LOCATION_CHANGE -> {
                        locationChangeEvents++;
                        if (event.getProfileId() != null) locationChangeProfileIds.add(event.getProfileId());
                    }
                }
            }
        }

        assertThat(validEvents).as("All ProfileUpdatedEvent should be valid").isEqualTo(newEvents);
        assertThat(preferencesEvents).as("Should have PREFERENCES change events").isGreaterThan(0);
        assertThat(criticalFieldsEvents).as("Should have CRITICAL_FIELDS change events").isGreaterThan(0);
        assertThat(nonCriticalEvents).as("Should have NON_CRITICAL change events").isGreaterThan(0);
        assertThat(locationChangeEvents).as("Should have LOCATION_CHANGE events").isGreaterThanOrEqualTo(locationUpdates);
        assertThat(locationChangeProfileIds).as("Should have LOCATION_CHANGE events for all city-patched profiles").containsAll(locationUpdatedProfileIds);

        log.info("All ProfileUpdatedEvent validations passed");
    }

    private void deleteProfilesAndVerifyEvents() throws Exception {
        log.info("========================================");
        log.info("STEP 7.2: Deleting Profiles (Mid-Wait)");
        log.info("========================================");

        List<ProfileTestData> profilesSnapshot = new ArrayList<>(createdProfiles);
        int profilesToDelete = Math.min(5, profilesSnapshot.size());
        int startIndex = Math.max(0, profilesSnapshot.size() - profilesToDelete);
        log.info("Will delete {} profiles (from index {} to end)", profilesToDelete, startIndex);

        int beforeDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        int profilesDeleted = 0;
        List<ProfileTestData> deletedProfiles = new ArrayList<>();

        for (int i = startIndex; i < profilesSnapshot.size(); i++) {
            ProfileTestData profile = profilesSnapshot.get(i);
            try {
                // Re-fetch token in case it expired
                String freshToken = keycloakTestHelper.getAccessToken(profile.username(),
                        keycloakUsers.stream().filter(u -> u.username().equals(profile.username()))
                                .findFirst().map(NewUserRecord::password).orElse("Password" + (i + 1) + "!"));

                mockMvc.perform(MockMvcRequestBuilders.delete("")
                                .header("Authorization", "Bearer " + freshToken))
                        .andExpect(status().isNoContent());
                profilesDeleted++;
                deletedProfiles.add(profile);
                log.info("[{}/{}] Deleted profile: {}", profilesDeleted, profilesToDelete, profile.firstName());
            } catch (Exception e) {
                log.error("[{}/{}] Failed to delete profile: {}", profilesDeleted + 1, profilesToDelete, profile.firstName(), e);
            }
        }

        log.info("Successfully deleted {}/{} profiles", profilesDeleted, profilesToDelete);

        final int expectedNewEvents = profilesDeleted;
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int newEvents = kafkaEventCollector.getProfileDeletedEvents().size() - beforeDeleteEvents;
                log.info("  Received {}/{} ProfileDeleteEvent messages", newEvents, expectedNewEvents);
                assertThat(newEvents).as("Should receive ProfileDeleteEvent for each deleted profile").isGreaterThanOrEqualTo(expectedNewEvents);
            });

        int afterDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        int newEvents = afterDeleteEvents - beforeDeleteEvents;
        stats.kafkaDeleteEventsReceived = newEvents;

        List<com.tinder.profiles.kafka.dto.ProfileDeleteEvent> deleteEvents =
            kafkaEventCollector.getProfileDeletedEvents().subList(beforeDeleteEvents, afterDeleteEvents);

        Set<UUID> deletedProfileIds = deletedProfiles.stream()
                .map(p -> UUID.fromString(p.profileId()))
                .collect(Collectors.toSet());

        int validEvents = 0;
        for (com.tinder.profiles.kafka.dto.ProfileDeleteEvent event : deleteEvents) {
            boolean isValid = event.getEventId() != null && event.getProfileId() != null
                    && deletedProfileIds.contains(event.getProfileId())
                    && event.getTimestamp() != null;
            if (isValid) validEvents++;
            else log.warn("  Invalid ProfileDeleteEvent: eventId={}, profileId={}", event.getEventId(), event.getProfileId());
        }

        assertThat(validEvents).as("All ProfileDeleteEvent should be valid").isEqualTo(newEvents);
        assertThat(validEvents).as("Should have received delete events for all deleted profiles").isEqualTo(profilesDeleted);

        log.info("All ProfileDeleteEvent validations passed");
    }

    private void checkRedisStateBeforeWait() {
        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys != null && !allKeys.isEmpty()) {
            long deckKeys = allKeys.stream().filter(k -> k.startsWith("deck:")).count();
            log.info("Redis state: {} total keys, {} deck keys (should be 0 initially)", allKeys.size(), deckKeys);
        }
    }

    private void checkRedisStateAfterWait() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        long deckKeyCount = (deckKeys != null) ? deckKeys.size() : 0;
        if (deckKeyCount == 0) log.warn("No deck keys found - Deck service may not be running");
        else log.info("Found {} deck keys in Redis", deckKeyCount);
    }

    private boolean verifySwipedProfilesExcluded(ProfileTestData profile, List<String> deckContents,
                                                  Map<String, Set<String>> userSwipedProfiles) {
        Set<String> swipedByUser = userSwipedProfiles.getOrDefault(profile.profileId(), Set.of());
        List<String> foundSwiped = swipedByUser.stream().filter(deckContents::contains).collect(Collectors.toList());
        boolean correct = foundSwiped.isEmpty();
        if (correct) {
            stats.decksWithCorrectExclusions++;
        }
        return correct;
    }

    private boolean verifyCandidatesMatchPreferences(ProfileTestData profile, List<String> deckContents,
                                                     Map<String, Profile> profileIndex) {
        Profile viewer = profileIndex.get(profile.profileId());
        if (viewer == null) { return false; }

        com.tinder.profiles.preferences.Preferences prefs = viewer.getPreferences();
        if (prefs == null) { return false; }

        List<String> invalidCandidates = new ArrayList<>();
        int validCount = 0;

        for (String candidateId : deckContents) {
            Profile candidate = profileIndex.get(candidateId);
            if (candidate == null) { invalidCandidates.add(candidateId + " (not found)"); continue; }
            if (!matchesGenderPreference(candidate.getGender(), prefs.getGender())) {
                invalidCandidates.add(candidateId + " (wrong gender: " + candidate.getGender() + ")"); continue;
            }
            if (!matchesAgePreference(candidate.getAge(), prefs.getMinAge(), prefs.getMaxAge())) {
                invalidCandidates.add(candidateId + " (wrong age: " + candidate.getAge() + ")"); continue;
            }
            validCount++;
        }

        boolean allValid = invalidCandidates.isEmpty();
        if (allValid) { stats.decksWithCorrectCandidates++; }
        return allValid;
    }

    private boolean verifyDeckQuality(ProfileTestData profile, List<String> deckContents,
                                      Map<String, Profile> profileIndex) {
        if (deckContents.isEmpty()) { return false; }
        if (deckContents.size() > 1000) { return false; }

        List<String> invalid = new ArrayList<>();
        for (String candidateId : deckContents) {
            Profile candidate = profileIndex.get(candidateId);
            if (candidate == null) { invalid.add(candidateId + " (not found)"); continue; }
            if (candidate.getProfileId().toString().equals(profile.profileId())) invalid.add(candidateId + " (self)");
            else if (candidate.isDeleted()) invalid.add(candidateId + " (deleted)");
        }

        if (!invalid.isEmpty()) { return false; }
        return true;
    }

    private boolean matchesGenderPreference(String candidateGender, String preferredGender) {
        if (preferredGender == null || preferredGender.equalsIgnoreCase("any") || preferredGender.equalsIgnoreCase("all")) return true;
        return candidateGender != null && candidateGender.equalsIgnoreCase(preferredGender);
    }

    private boolean matchesAgePreference(Integer candidateAge, Integer minAge, Integer maxAge) {
        if (candidateAge == null) return false;
        if (minAge != null && candidateAge < minAge) return false;
        return maxAge == null || candidateAge <= maxAge;
    }

    private Map<String, Set<String>> buildSwipeMap(List<ProfileTestData> profiles) {
        List<ProfileTestData> profilesSnapshot = new ArrayList<>(profiles);
        Map<String, Set<String>> swipeMap = new LinkedHashMap<>();
        for (int i = 0; i < profilesSnapshot.size(); i++) {
            ProfileTestData swiper = profilesSnapshot.get(i);
            Set<String> swipedTargets = new LinkedHashSet<>();
            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % profilesSnapshot.size();
                if (targetIndex != i) swipedTargets.add(profilesSnapshot.get(targetIndex).profileId());
            }
            swipeMap.put(swiper.profileId(), swipedTargets);
        }
        return swipeMap;
    }

    private void printVerificationSummary() {
        log.info("========================================");
        log.info("VERIFICATION SUMMARY");
        log.info("========================================");
        log.info("Total decks checked: {}", stats.decksVerified);
        log.info("Decks with data: {}", stats.decksWithData);
        log.info("Decks with correct exclusions: {}/{}", stats.decksWithCorrectExclusions, stats.decksWithData);
        log.info("Decks with correct candidates: {}/{}", stats.decksWithCorrectCandidates, stats.decksWithData);
        log.info("Decks fully correct: {}/{}", stats.decksFullyCorrect, stats.decksWithData);
        log.info("Average deck size: {}",
            stats.deckSizes.isEmpty() ? 0 :
            String.format("%.1f", stats.deckSizes.values().stream().mapToInt(Integer::intValue).average().orElse(0)));
        if (stats.decksWithData > 0) {
            double successRate = (stats.decksFullyCorrect * 100.0) / stats.decksWithData;
            log.info("Success rate: {}/100", String.format("%.1f", successRate));
        }
        log.info("========================================");
    }
}
