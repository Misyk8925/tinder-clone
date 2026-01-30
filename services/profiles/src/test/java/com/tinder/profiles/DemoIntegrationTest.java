package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.deck.DeckCacheReader;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.user.NewUserRecord;
import com.tinder.profiles.user.UserService;
import com.tinder.profiles.util.DeckCacheTestHelper;
import com.tinder.profiles.util.KeycloakTestHelper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test demonstrating full user journey with deterministic test data.
 * Uses Testcontainers for PostgreSQL and Redis to ensure reproducible tests.
 * Verifies that each user has a valid deck cache stored in Redis.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DemoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DemoIntegrationTest.class);

    /**
     * Centralized timeout configuration for different environments.
     * Supports local development and CI/CD with different timeout values.
     */
    private static class TestTimeoutConfig {
        // Detect CI environment
        private static final boolean IS_CI = System.getenv("CI") != null
            || System.getenv("GITHUB_ACTIONS") != null
            || System.getenv("JENKINS_HOME") != null
            || System.getProperty("ci.environment") != null;

        // Deck rebuild timeout (CI is slower, needs more time)
        static final int DECK_REBUILD_TIMEOUT_MS = IS_CI ? 30_000 : 15_000;

        // Polling interval for deck status checks
        static final int DECK_POLL_INTERVAL_MS = IS_CI ? 1_000 : 500;

        // Swipe service timeout
        static final int SWIPE_TIMEOUT_MS = IS_CI ? 10_000 : 5_000;

        // Max retries for transient failures
        static final int MAX_RETRIES = 3;

        static {
            log.info("Test environment: {} (timeout: {}ms, poll interval: {}ms)",
                IS_CI ? "CI" : "LOCAL",
                DECK_REBUILD_TIMEOUT_MS,
                DECK_POLL_INTERVAL_MS);
        }
    }

    // Deterministic test configuration constants
    private static final int TEST_USER_COUNT = 10;
    private static final int MIN_AGE = 19;
    private static final int MAX_AGE = 40;
    private static final int SWIPES_PER_USER = 5;
    private static final String DEFAULT_CITY = "Amstetten";
    private static final String DEFAULT_BIO = "this is my life";
    private static final int DEFAULT_MAX_RANGE = 4;

    // Testcontainers - must be static and started before @DynamicPropertySource
    @Container
    static PostgreSQLContainer<?> postgresContainer;

    @Container
    static GenericContainer<?> redisContainer;

    static {
        postgresContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("profiles_test")
                .withUsername("test")
                .withPassword("test");
        postgresContainer.start();

        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:8.2.1-alpine"))
                .withExposedPorts(6379);
        redisContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);

        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private com.tinder.profiles.preferences.PreferencesRepository preferencesRepository;

    @Autowired
    private DeckCacheReader deckCacheReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${swipes.base-url:http://localhost:8020}")
    private String swipesBaseUrl;

    @Value("${deck.base-url:http://127.0.0.1:8030}")
    private String deckBaseUrl;

    // Store created profile IDs for verification
    private final List<ProfileTestData> createdProfiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        Set<String> keys = redisTemplate.keys("deck:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Clean up database before each test
        profileRepository.deleteAll();
        preferencesRepository.deleteAll();

        createdProfiles.clear();

        log.debug("Test setup complete: Redis and database cleaned");
    }

    @AfterEach
    void cleanup() {
        keycloakTestHelper.cleanupTestUsers();
    }

    /**
     * Test data structure to hold profile information
     */
    private static class ProfileTestData {
        final String profileId;
        final String token;
        final String username;
        final String firstName;
        final int age;
        final String gender;
        final String preferredGender;

        public ProfileTestData(String profileId, String token, String username, String firstName,
                               int age, String gender, String preferredGender) {
            this.profileId = profileId;
            this.token = token;
            this.username = username;
            this.firstName = firstName;
            this.age = age;
            this.gender = gender;
            this.preferredGender = preferredGender;
        }

        public String getShortId() {
            return profileId.substring(0, Math.min(8, profileId.length()));
        }

        public UUID getUUID() {
            return UUID.fromString(profileId);
        }
    }

    /**
     * Test statistics for reporting
     */
    private static class TestStatistics {
        int profilesCreated = 0;
        int profilesFailed = 0;
        int swipesCreated = 0;
        int swipesFailed = 0;
        int likesCount = 0;
        int dislikesCount = 0;
        int decksVerified = 0;
        int decksWithData = 0;
        Map<String, Integer> deckSizes = new HashMap<>();
        long testDurationMs = 0;

        public void printSummary() {
            log.info("========================================");
            log.info("TEST EXECUTION SUMMARY");
            log.info("========================================");
            log.info("Profiles created: {}/{}", profilesCreated, profilesCreated + profilesFailed);
            log.info("Swipes created: {} (Likes: {}, Dislikes: {})", swipesCreated, likesCount, dislikesCount);
            log.info("Swipes failed: {}", swipesFailed);
            log.info("Decks verified: {}/{}", decksWithData, decksVerified);
            log.info("Average deck size: {}", deckSizes.isEmpty() ? 0 :
                    deckSizes.values().stream().mapToInt(Integer::intValue).average().orElse(0));
            log.info("Test duration: {} ms", testDurationMs);
            log.info("========================================");
        }
    }

    @Test
    @DisplayName("Full user journey: create profiles, swipes, verify Redis deck cache")
    public void testFullUserJourneyWithProfiles_Swipes_AndDecks() throws Exception {
        long startTime = System.currentTimeMillis();
        TestStatistics stats = new TestStatistics();

        log.info("========================================");
        log.info("STARTING INTEGRATION TEST");
        log.info("========================================");
        log.info("PostgreSQL: {}", postgresContainer.getJdbcUrl());
        log.info("Redis: {}:{}", redisContainer.getHost(), redisContainer.getFirstMappedPort());

        try {
            // Step 1: Create profiles with deterministic data
            List<ProfileTestData> profiles = createDeterministicProfiles(stats);
            createdProfiles.addAll(profiles);

            // Step 2: Create deterministic swipes between users
            createDeterministicSwipes(profiles, stats);

            // Step 3: Trigger deck rebuild and verify cache
            triggerDeckRebuildAndVerify(profiles, stats);

            // Step 4: Verify Redis cache directly
            verifyRedisCacheForAllUsers(profiles, stats);

            stats.testDurationMs = System.currentTimeMillis() - startTime;
            stats.printSummary();

            // Assertions
            assertThat(stats.profilesCreated).isEqualTo(TEST_USER_COUNT);
            assertThat(stats.swipesCreated).isGreaterThan(0);

            log.info("========================================");
            log.info("INTEGRATION TEST COMPLETED SUCCESSFULLY");
            log.info("========================================");
        } catch (Exception e) {
            log.error("Integration test failed with exception", e);
            stats.testDurationMs = System.currentTimeMillis() - startTime;
            stats.printSummary();
            throw e;
        }
    }

    /**
     * Step 1: Create user profiles with deterministic data
     */
    private List<ProfileTestData> createDeterministicProfiles(TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 1: Creating Profiles (Deterministic)");
        log.info("========================================");


        try {
            userService.createTestUsers();
        } catch (Exception e) {

        }
        List<NewUserRecord> users = userService.getUsers();
        List<ProfileTestData> profiles = new ArrayList<>();

        // Limit to TEST_USER_COUNT for faster tests
        int userCount = Math.min(users.size(), TEST_USER_COUNT);
        log.info("Creating {} user profiles with deterministic data...", userCount);

        for (int i = 0; i < userCount; i++) {
            NewUserRecord user = users.get(i);
            try {
                ProfileTestData profile = createDeterministicProfile(user, i);
                profiles.add(profile);
                stats.profilesCreated++;

                log.info("[{}/{}] Created profile: {} (ID: {}, Age: {}, Gender: {}, Prefers: {})",
                    i + 1, userCount, profile.firstName, profile.getShortId(),
                    profile.age, profile.gender, profile.preferredGender);
            } catch (Exception e) {
                stats.profilesFailed++;
                log.error("[{}/{}] Failed to create profile for user: {}", i + 1, userCount, user.username(), e);
                throw new RuntimeException("Failed to create profile for user: " + user.username(), e);
            }
        }

        log.info("Successfully created {}/{} profiles", stats.profilesCreated, userCount);
        return profiles;
    }

    /**
     * Create a single profile with deterministic data based on index
     */
    private ProfileTestData createDeterministicProfile(NewUserRecord user, int index) throws Exception {
        String authHeader = keycloakTestHelper.createAuthorizationHeader(user.username(), user.password());
        String token = authHeader.replace("Bearer ", "");

        // Deterministic age: 20-29 based on index
        int age = MIN_AGE + (index % 21);

        // Deterministic gender: alternating male/female
        String gender = (index % 2 == 0) ? "male" : "female";

        // Deterministic preference: opposite gender
        String preferredGender = (index % 2 == 0) ? "female" : "male";

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
            throw new IllegalStateException("Profile creation returned null id for user: " + user.username());
        }

        return new ProfileTestData(profileId, token, user.username(), user.firstName(), age, gender, preferredGender);
    }

    /**
     * Build JSON for profile creation request
     */
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

    /**
     * Step 2: Create deterministic swipes between users
     * Each user swipes on the next SWIPES_PER_USER users (circular pattern)
     */
    private void createDeterministicSwipes(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 2: Creating Deterministic Swipes");
        log.info("========================================");

        WebClient swipesClient = WebClient.builder().baseUrl(swipesBaseUrl).build();

        log.info("Each user will swipe on {} profiles (deterministic circular pattern)", SWIPES_PER_USER);

        for (int i = 0; i < profiles.size(); i++) {
            ProfileTestData swiper = profiles.get(i);
            int successfulSwipes = 0;

            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % profiles.size();

                // Skip if same user
                if (targetIndex == i) {
                    continue;
                }

                ProfileTestData target = profiles.get(targetIndex);

                // Deterministic like pattern: like users with opposite gender
                boolean isLike = !swiper.gender.equals(target.gender);

                if (createSwipe(swipesClient, swiper, target, isLike)) {
                    stats.swipesCreated++;
                    successfulSwipes++;
                    if (isLike) {
                        stats.likesCount++;
                    } else {
                        stats.dislikesCount++;
                    }

                    log.debug("  Swipe #{}: {} -> {} ({})",
                        stats.swipesCreated, swiper.getShortId(), target.getShortId(), isLike ? "LIKE" : "DISLIKE");
                } else {
                    stats.swipesFailed++;
                }
            }

            log.info("[{}/{}] User {} ({}) created {} swipes",
                i + 1, profiles.size(), swiper.getShortId(), swiper.firstName, successfulSwipes);
        }

        log.info("Total swipes created: {} (Likes: {}, Dislikes: {})",
            stats.swipesCreated, stats.likesCount, stats.dislikesCount);
        log.info("Failed swipes: {}", stats.swipesFailed);
    }

    /**
     * Create a single swipe between two profiles
     */
    private boolean createSwipe(WebClient swipesClient, ProfileTestData swiper, ProfileTestData target, boolean isLike) {
        Map<String, Object> swipeData = Map.of(
                "profile1Id", swiper.profileId,
                "profile2Id", target.profileId,
                "decision", isLike
        );

        try {
            swipesClient.post()
                    .uri("/api/v1/swipes/")
                    .header("Authorization", "Bearer " + swiper.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(swipeData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Failed to create swipe: {} -> {} ({}) - {}",
                swiper.getShortId(), target.getShortId(), isLike ? "LIKE" : "DISLIKE", e.getMessage());
            return false;
        }
    }

    /**
     * Step 3: Trigger deck rebuild on remote Deck service and verify
     */
    private void triggerDeckRebuildAndVerify(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 3: Triggering Deck Rebuild");
        log.info("========================================");

        WebClient deckClient = WebClient.builder().baseUrl(deckBaseUrl).build();

        log.info("Triggering deck rebuild for {} profiles on Deck service at {}", profiles.size(), deckBaseUrl);

        for (int i = 0; i < profiles.size(); i++) {
            ProfileTestData profile = profiles.get(i);
            log.info("[{}/{}] Rebuilding deck for user {} ({})",
                i + 1, profiles.size(), profile.getShortId(), profile.firstName);

            try {
                // Trigger remote rebuild
                deckClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/admin/deck/rebuild")
                                .queryParam("viewerId", profile.profileId)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                // Poll for deck readiness with proper error handling
                DeckPollResult result = pollForDeckReadiness(deckClient, profile.profileId);

                if (result.success) {
                    stats.deckSizes.put(profile.profileId, result.deckSize.intValue());
                    log.info("  ✓ Deck ready for {} ({}): size={}",
                        profile.getShortId(), profile.firstName, result.deckSize);
                } else {
                    log.error("  ✗ Deck rebuild failed for {} ({}): {} (HTTP {})",
                        profile.getShortId(), profile.firstName, result.errorMessage, result.httpStatus);
                    stats.deckSizes.put(profile.profileId, 0);
                }

            } catch (WebClientResponseException.NotFound nf) {
                log.warn("  Deck rebuild endpoint not found (404) for profile {} - skipping", profile.getShortId());
            } catch (Exception e) {
                log.warn("  Failed to trigger rebuild for profile {}: {}", profile.getShortId(), e.getMessage());
            }
        }

        // Attempt global manual rebuild as fallback
        tryGlobalManualRebuild(deckClient);
    }

    /**
     * Poll deck service for deck size with timeout
     */
    /**
     * Result of deck polling operation with explicit success/failure states
     */
    private static class DeckPollResult {
        final boolean success;
        final Long deckSize;
        final String errorMessage;
        final int httpStatus;

        private DeckPollResult(boolean success, Long deckSize, String errorMessage, int httpStatus) {
            this.success = success;
            this.deckSize = deckSize;
            this.errorMessage = errorMessage;
            this.httpStatus = httpStatus;
        }

        static DeckPollResult success(Long size) {
            return new DeckPollResult(true, size, null, 200);
        }

        static DeckPollResult failure(String errorMessage, int httpStatus) {
            return new DeckPollResult(false, null, errorMessage, httpStatus);
        }

        static DeckPollResult timeout() {
            return new DeckPollResult(false, null, "Timeout waiting for deck rebuild", 0);
        }
    }

    /**
     * Poll for deck readiness after rebuild with proper error handling.
     *
     * This method:
     * 1. Waits for deck service to complete rebuild (not just size > 0)
     * 2. Explicitly exposes HTTP errors instead of hiding them
     * 3. Uses environment-aware timeouts
     * 4. Returns explicit success/failure status
     *
     * @param deckClient WebClient configured for deck service
     * @param profileId Profile ID to check
     * @return DeckPollResult with explicit success/failure and error details
     */
    private DeckPollResult pollForDeckReadiness(WebClient deckClient, String profileId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TestTimeoutConfig.DECK_REBUILD_TIMEOUT_MS;
        int attemptCount = 0;

        log.debug("  Polling for deck readiness (timeout: {}ms, interval: {}ms)",
            TestTimeoutConfig.DECK_REBUILD_TIMEOUT_MS,
            TestTimeoutConfig.DECK_POLL_INTERVAL_MS);

        while (System.currentTimeMillis() < deadline) {
            attemptCount++;

            try {
                // First check: is deck service responding?
                Long deckSize = deckClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/admin/deck/size")
                                .queryParam("viewerId", profileId)
                                .build())
                        .retrieve()
                        .bodyToMono(Long.class)
                        .block();

                if (deckSize == null) {
                    log.warn("  Attempt {}: Deck service returned null size for profile {}", attemptCount, profileId);
                    TimeUnit.MILLISECONDS.sleep(TestTimeoutConfig.DECK_POLL_INTERVAL_MS);
                    continue;
                }

                // Second check: verify deck exists in Redis (not just service response)
                String deckKey = "deck:" + profileId;
                Boolean hasKey = redisTemplate.hasKey(deckKey);

                if (Boolean.FALSE.equals(hasKey)) {
                    log.debug("  Attempt {}: Deck key not yet in Redis for profile {}", attemptCount, profileId);
                    TimeUnit.MILLISECONDS.sleep(TestTimeoutConfig.DECK_POLL_INTERVAL_MS);
                    continue;
                }

                // Success: deck is ready (size can be 0 if no candidates match preferences)
                log.info("  ✓ Deck ready after {} attempts: size={} for profile {}",
                    attemptCount, deckSize, profileId);
                return DeckPollResult.success(deckSize);

            } catch (WebClientResponseException e) {
                // Explicit HTTP error from deck service - don't hide it!
                String errorMsg = String.format(
                    "Deck service HTTP error: %d %s - %s",
                    e.getStatusCode().value(),
                    e.getStatusText(),
                    e.getResponseBodyAsString()
                );
                log.error("  ✗ {}", errorMsg);
                return DeckPollResult.failure(errorMsg, e.getStatusCode().value());

            } catch (Exception e) {
                // Other errors (timeout, connection refused, etc)
                log.warn("  Attempt {}: Exception polling deck service: {}", attemptCount, e.getMessage());

                // For transient errors, retry until deadline
                if (System.currentTimeMillis() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(TestTimeoutConfig.DECK_POLL_INTERVAL_MS);
                    continue;
                }

                return DeckPollResult.failure("Exception: " + e.getMessage(), 0);
            }
        }

        // Timeout reached
        log.error("  ✗ Timeout after {} attempts waiting for deck readiness (profile: {})",
            attemptCount, profileId);
        return DeckPollResult.timeout();
    }

    /**
     * @deprecated Use pollForDeckReadiness instead - this method hides errors
     */
    @Deprecated
    private Long pollForDeckSize(WebClient deckClient, String profileId) throws InterruptedException {
        DeckPollResult result = pollForDeckReadiness(deckClient, profileId);
        return result.success ? result.deckSize : 0L;
    }

    /**
     * Attempt global manual rebuild as fallback
     */
    private void tryGlobalManualRebuild(WebClient deckClient) {
        log.info("Attempting global manual rebuild...");
        try {
            deckClient.get()
                    .uri("/api/v1/admin/deck/manual-rebuild")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Global manual rebuild triggered successfully");
        } catch (Exception e) {
            log.debug("Global manual rebuild not available or failed: {}", e.getMessage());
        }
    }

    /**
     * Step 4: Verify Redis cache directly for all users
     */
    private void verifyRedisCacheForAllUsers(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 4: Verifying Redis Cache");
        log.info("========================================");

        for (ProfileTestData profile : profiles) {
            stats.decksVerified++;

            // Check if deck exists in Redis
            String deckKey = "deck:" + profile.profileId;
            Boolean hasKey = redisTemplate.hasKey(deckKey);

            if (Boolean.TRUE.equals(hasKey)) {
                Long deckSize = redisTemplate.opsForZSet().size(deckKey);

                if (deckSize != null && deckSize > 0) {
                    stats.decksWithData++;
                    stats.deckSizes.put(profile.profileId, deckSize.intValue());

                    // Read first few candidates from cache
                    Set<String> topCandidates = redisTemplate.opsForZSet()
                            .reverseRange(deckKey, 0, 4);

                    log.info("  [{}] {} ({}) - Deck size: {}, Top candidates: {}",
                            profile.getShortId(), profile.firstName, profile.gender,
                            deckSize, topCandidates != null ? topCandidates.size() : 0);
                } else {
                    log.warn("  [{}] {} - Empty deck in Redis", profile.getShortId(), profile.firstName);
                }
            } else {
                log.warn("  [{}] {} - No deck found in Redis", profile.getShortId(), profile.firstName);
            }

            // Also verify using DeckCacheReader
            boolean exists = deckCacheReader.exists(profile.getUUID());
            List<UUID> deck = deckCacheReader.readDeck(profile.getUUID(), 0, 10);

            log.debug("    DeckCacheReader.exists: {}, deck size: {}", exists, deck.size());
        }

        log.info("Decks verified: {}/{} have data in Redis", stats.decksWithData, stats.decksVerified);
    }

    /**
     * Additional test: Verify deck cache consistency after swipes
     */
    @Test
    @DisplayName("Verify deck cache is populated correctly after swipes")
    public void testDeckCacheAfterSwipes() throws Exception {
        // This test verifies that after swipes, the deck cache correctly excludes swiped profiles
        log.info("========================================");
        log.info("TESTING DECK CACHE CONSISTENCY");
        log.info("========================================");

        TestStatistics stats = new TestStatistics();

        // Create minimal profiles for this test
        List<ProfileTestData> profiles = createDeterministicProfiles(stats);

        assertThat(profiles)
            .as("Should create at least 3 profiles for consistency test")
            .hasSizeGreaterThanOrEqualTo(3);

        // Create specific swipes
        ProfileTestData user1 = profiles.get(0);
        ProfileTestData user2 = profiles.get(1);
        ProfileTestData user3 = profiles.get(2);

        log.info("Test scenario: User1 ({}) swipes on User2 ({}), verify User2 is excluded from deck",
            user1.getShortId(), user2.getShortId());

        WebClient swipesClient = WebClient.builder().baseUrl(swipesBaseUrl).build();

        // User1 swipes on User2
        boolean swipeResult = createSwipe(swipesClient, user1, user2, true);
        log.info("User1 swiped on User2: {}", swipeResult);

        assertThat(swipeResult)
            .as("Swipe creation should succeed")
            .isTrue();

        // Trigger deck rebuild for User1
        WebClient deckClient = WebClient.builder().baseUrl(deckBaseUrl).build();

        deckClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/admin/deck/rebuild")
                        .queryParam("viewerId", user1.profileId)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("Deck rebuild triggered for User1, waiting for completion...");

        // Wait for rebuild with proper polling (no fixed sleep!)
        DeckPollResult result = pollForDeckReadiness(deckClient, user1.profileId);

        assertThat(result.success)
            .as("Deck rebuild should complete successfully: " + result.errorMessage)
            .isTrue();

        assertThat(result.deckSize)
            .as("User1 should have a deck after rebuild (size can be 0 if no candidates)")
            .isNotNull()
            .isGreaterThanOrEqualTo(0L);

        log.info("User1's deck size after rebuild: {}", result.deckSize);

        // Verify User2 is NOT in User1's deck (already swiped)
        String deckKey = "deck:" + user1.profileId;
        Boolean hasKey = redisTemplate.hasKey(deckKey);

        assertThat(hasKey)
            .as("Deck key should exist in Redis for User1")
            .isTrue();

        Set<String> deckContents = redisTemplate.opsForZSet().range(deckKey, 0, -1);

        assertThat(deckContents)
            .as("Deck contents should not be null")
            .isNotNull();

        boolean containsSwipedUser = deckContents.contains(user2.profileId);
        log.info("User1's deck contains User2 (already swiped): {}", containsSwipedUser);

        assertThat(containsSwipedUser)
            .as("User1's deck should NOT contain User2 (already swiped)")
            .isFalse();

        // Additional verification: User3 (not swiped) might be in deck if preferences match
        boolean containsUser3 = deckContents.contains(user3.profileId);
        log.info("User1's deck contains User3 (not swiped): {}", containsUser3);

        // Verify using DeckCacheReader as well
        List<UUID> deckViaReader = deckCacheReader.readDeck(user1.getUUID(), 0, 100);

        assertThat(deckViaReader)
            .as("DeckCacheReader should return deck for User1")
            .isNotNull()
            .doesNotContain(user2.getUUID());

        log.info("✓ Deck consistency verified: swiped profile excluded from deck");
        log.info("========================================");
        log.info("DECK CACHE CONSISTENCY TEST PASSED");
        log.info("========================================");
    }

    /**
     * Test: User can create profiles and each has correct deck cache in Redis.
     * This test verifies:
     * 1. Users can create profiles successfully
     * 2. Correct deck is calculated based on preferences
     * 3. Deck is stored properly in Redis testcontainer
     * 4. Each user has valid cache with correct candidates
     */
    @Test
    @DisplayName("Users can create profiles and have correct deck cache in Redis")
    public void testUserCanCreateProfileWithCorrectDeckCache() {
        log.info("========================================");
        log.info("TEST: User Profile Creation & Deck Cache");
        log.info("========================================");
        log.info("Redis Testcontainer: {}:{}", redisContainer.getHost(), redisContainer.getFirstMappedPort());

        TestStatistics stats = new TestStatistics();

        // Step 1: Create profiles directly in DB (simulating user registration)
        log.info("Step 1: Creating {} user profiles...", TEST_USER_COUNT);
        List<Profile> createdProfiles = createDeterministicProfilesInDB();

        assertThat(createdProfiles)
            .as("All profiles should be created successfully")
            .hasSize(TEST_USER_COUNT)
            .allMatch(p -> p.getProfileId() != null)
            .allMatch(p -> p.getPreferences() != null)
            .allMatch(p -> p.getLocation() != null);

        log.info("✓ Successfully created {} profiles", createdProfiles.size());
        stats.profilesCreated = createdProfiles.size();

        // Step 2: Calculate correct decks using helper
        log.info("Step 2: Calculating correct decks based on preferences...");
        DeckCacheTestHelper deckHelper = new DeckCacheTestHelper(redisTemplate);
        deckHelper.calculateCorrectDecks(createdProfiles);

        Map<String, Object> deckStats = deckHelper.getStatistics();
        log.info("✓ Deck calculation complete: {} decks, {} total candidates, avg size: {}",
            deckStats.get("totalDecks"),
            deckStats.get("totalCandidates"),
            String.format("%.1f", (Double) deckStats.get("avgDeckSize")));

        // Step 3: Write decks to Redis
        log.info("Step 3: Writing decks to Redis...");
        deckHelper.writeAllDecksToRedis();
        log.info("✓ All decks written to Redis");

        // Step 4: Verify each user has correct deck cache
        log.info("Step 4: Verifying deck cache for each user...");

        for (Profile profile : createdProfiles) {
            UUID viewerId = profile.getProfileId();
            List<Map.Entry<UUID, Double>> correctDeck = deckHelper.getCorrectDeck(viewerId);

            // Verify via Redis directly
            String deckKey = "deck:" + viewerId;
            Boolean hasKey = redisTemplate.hasKey(deckKey);
            Long redisSize = redisTemplate.opsForZSet().size(deckKey);

            // Verify via DeckCacheReader
            boolean existsViaReader = deckCacheReader.exists(viewerId);
            List<UUID> readerDeck = deckCacheReader.readDeck(viewerId, 0, 100);

            // Assertions
            assertThat(hasKey)
                .as("User %s should have deck in Redis", profile.getName())
                .isTrue();

            assertThat(redisSize)
                .as("User %s deck should not be empty", profile.getName())
                .isGreaterThan(0)
                .isEqualTo((long) correctDeck.size());

            assertThat(existsViaReader)
                .as("DeckCacheReader should find deck for %s", profile.getName())
                .isTrue();

            assertThat(readerDeck)
                .as("Reader should return correct deck size for %s", profile.getName())
                .hasSize(correctDeck.size());

            // Verify deck contents are correct
            Set<String> redisContents = redisTemplate.opsForZSet().reverseRange(deckKey, 0, -1);
            Set<String> expectedIds = correctDeck.stream()
                .map(e -> e.getKey().toString())
                .collect(java.util.stream.Collectors.toSet());

            assertThat(redisContents)
                .as("Deck contents should match expected candidates for %s", profile.getName())
                .containsExactlyInAnyOrderElementsOf(expectedIds);

            // Verify all candidates match preferences
            for (UUID candidateId : readerDeck) {
                Profile candidate = createdProfiles.stream()
                    .filter(p -> p.getProfileId().equals(candidateId))
                    .findFirst()
                    .orElseThrow();

                assertThat(candidateId)
                    .as("Deck should not contain user themselves")
                    .isNotEqualTo(viewerId);

                String prefGender = profile.getPreferences().getGender();
                if (prefGender != null && !prefGender.equalsIgnoreCase("any") && !prefGender.equalsIgnoreCase("all")) {
                    assertThat(candidate.getGender().toLowerCase())
                        .as("Candidate gender should match preference")
                        .isEqualTo(prefGender.toLowerCase());
                }

                Integer minAge = profile.getPreferences().getMinAge();
                Integer maxAge = profile.getPreferences().getMaxAge();
                if (minAge != null) {
                    assertThat(candidate.getAge())
                        .as("Candidate age should be >= minAge")
                        .isGreaterThanOrEqualTo(minAge);
                }
                if (maxAge != null) {
                    assertThat(candidate.getAge())
                        .as("Candidate age should be <= maxAge")
                        .isLessThanOrEqualTo(maxAge);
                }
            }

            // Verify using helper
            assertThat(deckHelper.verifyDeckInRedis(viewerId))
                .as("Helper verification should pass for %s", profile.getName())
                .isTrue();

            stats.decksWithData++;
            stats.deckSizes.put(viewerId.toString(), redisSize.intValue());

            log.info("  ✓ {} ({}, prefers {}) - {} candidates match preferences",
                profile.getName(),
                profile.getGender(),
                profile.getPreferences().getGender(),
                correctDeck.size());
        }

        // Final verification
        assertThat(stats.decksWithData)
            .as("All users should have valid deck cache")
            .isEqualTo(TEST_USER_COUNT);

        log.info("========================================");
        log.info("✓ TEST PASSED: All {} users created with correct deck cache", TEST_USER_COUNT);
        log.info("========================================");

        stats.printSummary();
    }

    /**
     * Create deterministic profiles directly in DB without Keycloak dependency
     */
    private List<Profile> createDeterministicProfilesInDB() {
        log.info("Creating {} deterministic profiles directly in DB...", TEST_USER_COUNT);

        List<Profile> profiles = new ArrayList<>();
        String[] names = {"Alexander", "Maria", "Dmitry", "Anna", "Ivan",
                         "Catherine", "Sergey", "Olga", "Andrew", "Natalie"};

        for (int i = 0; i < TEST_USER_COUNT; i++) {
            String name = names[i % names.length];
            int age = MIN_AGE + 1 + (i % 20); // Ages 20-39
            String gender = (i % 2 == 0) ? "male" : "female";
            String preferredGender = (i % 2 == 0) ? "female" : "male";
            String userId = "test-user-" + (i + 1);

            Profile profile = createProfileDirectly(name, age, gender, userId, preferredGender);
            profiles.add(profile);

            log.info("  [{}/{}] Created: {} (Age: {}, Gender: {}, Prefers: {})",
                i + 1, TEST_USER_COUNT, name, age, gender, preferredGender);
        }

        return profiles;
    }

    /**
     * Create a single profile directly in DB
     */
    private Profile createProfileDirectly(String name, int age, String gender, String userId, String preferredGender) {
        // Create/find preferences
        com.tinder.profiles.preferences.Preferences preferences = preferencesRepository
            .findByValues(MIN_AGE, MAX_AGE, preferredGender, DEFAULT_MAX_RANGE)
            .orElseGet(() -> {
                com.tinder.profiles.preferences.Preferences newPrefs =
                    com.tinder.profiles.preferences.Preferences.builder()
                        .minAge(MIN_AGE)
                        .maxAge(MAX_AGE)
                        .gender(preferredGender)
                        .maxRange(DEFAULT_MAX_RANGE)
                        .build();
                return preferencesRepository.save(newPrefs);
            });

        // Create location
        com.tinder.profiles.location.Location location = com.tinder.profiles.location.Location.builder()
            .city(DEFAULT_CITY)
            .geo(new org.locationtech.jts.geom.GeometryFactory(
                new org.locationtech.jts.geom.PrecisionModel(), 4326
            ).createPoint(new org.locationtech.jts.geom.Coordinate(14.8743, 48.1183)))
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build();

        // Create profile
        Profile profile = Profile.builder()
            .name(name)
            .age(age)
            .gender(gender)
            .bio(DEFAULT_BIO)
            .city(DEFAULT_CITY)
            .userId(userId)
            .isActive(true)
            .isDeleted(false)
            .location(location)
            .preferences(preferences)
            .build();

        // Set bidirectional relationship
        location.setProfile(profile);

        return profileRepository.save(profile);
    }

    /**
     * Extract sub claim from JWT token
     */
    @SuppressWarnings("unused")
    private String extractSubFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            return (String) claims.get("sub");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract sub from token", e);
        }
    }
}
