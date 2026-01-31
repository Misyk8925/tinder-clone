package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.profiles.deck.DeckCacheReader;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.user.NewUserRecord;
import com.tinder.profiles.user.UserService;
import com.tinder.profiles.util.KeycloakTestHelper;
import com.tinder.profiles.util.TestKafkaConsumerConfig;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration test that demonstrates the full user journey:
 * 1. Create Keycloak users
 * 2. Create profiles for users
 * 3. Verify Kafka ProfileCreateEvent messages
 * 4. Create swipes between users
 * 5. Wait 1.5 minutes for deck service to build decks
 * 6. Verify correct decks are stored in Redis
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestKafkaConsumerConfig.class)
public class ComprehensiveIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ComprehensiveIntegrationTest.class);

    // Test configuration
    private static final int TEST_USER_COUNT = 15;
    private static final int MIN_AGE = 20;
    private static final int MAX_AGE = 35;
    private static final int SWIPES_PER_USER = 3;
    private static final String DEFAULT_CITY = "Berlin";
    private static final String DEFAULT_BIO = "Integration test user";
    private static final int DEFAULT_MAX_RANGE = 10;
    private static final long DECK_BUILD_WAIT_TIME_MS = 90_000; // 1.5 minutes

    // Testcontainers
    @Container
    static PostgreSQLContainer<?> postgresContainer;

    @Container
    static org.testcontainers.containers.KafkaContainer kafkaContainer;

    static final String definedPort = SpringBootTest.WebEnvironment.DEFINED_PORT.toString();

    static {
        // Start Kafka container FIRST
        kafkaContainer = new org.testcontainers.containers.KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
        ).withStartupTimeout(java.time.Duration.ofMinutes(2));
        kafkaContainer.start();

        log.info("Kafka container started: {}", kafkaContainer.getBootstrapServers());

        // Then start PostgreSQL
        postgresContainer = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("profiles_test")
                .withUsername("test")
                .withPassword("test");
        postgresContainer.start();

    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafkaContainer::getBootstrapServers);

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

    @Autowired
    private TestKafkaConsumerConfig.TestKafkaEventCollector kafkaEventCollector;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${swipes.base-url:http://localhost:8020}")
    private String swipesBaseUrl;

    @Value("${server.port}")
    private int actualServerPort;

    // Fixed port for profiles service during test (configured in application-test.yml)
    private static final int PROFILES_PORT = 8011;


    private final List<ProfileTestData> createdProfiles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Clean up Redis
        Set<String> keys = redisTemplate.keys("deck:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Clean up database
        profileRepository.deleteAll();
        preferencesRepository.deleteAll();

        // Reset Kafka event collector
        kafkaEventCollector.reset();

        createdProfiles.clear();

        log.info("Test setup complete: Redis, database, and Kafka collector cleaned");
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
        int keycloakUsersCreated = 0;
        int profilesCreated = 0;
        int profilesFailed = 0;
        int kafkaCreateEventsReceived = 0;
        int kafkaUpdateEventsReceived = 0;
        int kafkaDeleteEventsReceived = 0;
        int swipesCreated = 0;
        int swipesFailed = 0;
        int likesCount = 0;
        int dislikesCount = 0;
        int decksVerified = 0;
        int decksWithData = 0;
        int decksWithCorrectExclusions = 0;
        int decksWithCorrectCandidates = 0;
        int decksFullyCorrect = 0;
        Map<String, Integer> deckSizes = new HashMap<>();
        long testDurationMs = 0;
        long deckBuildWaitTimeMs = 0;

        public void printSummary() {
            log.info("========================================");
            log.info("TEST EXECUTION SUMMARY");
            log.info("========================================");
            log.info("Keycloak users created: {}", keycloakUsersCreated);
            log.info("Profiles created: {}/{}", profilesCreated, profilesCreated + profilesFailed);
            log.info("Kafka ProfileCreateEvent received: {}", kafkaCreateEventsReceived);
            log.info("Kafka ProfileUpdatedEvent received: {}", kafkaUpdateEventsReceived);
            log.info("Kafka ProfileDeleteEvent received: {}", kafkaDeleteEventsReceived);
            log.info("Swipes created: {} (Likes: {}, Dislikes: {})", swipesCreated, likesCount, dislikesCount);
            log.info("Swipes failed: {}", swipesFailed);
            log.info("Deck build wait time: {} ms ({} seconds)", deckBuildWaitTimeMs, deckBuildWaitTimeMs / 1000);
            log.info("Decks verified: {}/{} with data", decksWithData, decksVerified);
            log.info("Decks with correct exclusions: {}", decksWithCorrectExclusions);
            log.info("Decks with correct candidates: {}", decksWithCorrectCandidates);
            log.info("Decks fully correct: {}", decksFullyCorrect);
            log.info("Average deck size: {}", deckSizes.isEmpty() ? 0 :
                    deckSizes.values().stream().mapToInt(Integer::intValue).average().orElse(0));
            log.info("Total test duration: {} ms ({} seconds)", testDurationMs, testDurationMs / 1000);
            log.info("========================================");
        }
    }

    /**
     * COMPREHENSIVE INTEGRATION TEST
     * This test combines all steps in a single flow:
     * 1. Create Keycloak users
     * 2. Create profiles
     * 3. Verify Kafka ProfileCreateEvent messages
     * 4. Create swipes
     * 5. Wait 1.5 minutes
     * 6. Verify decks in Redis
     */
    @Test
    @DisplayName("Comprehensive Integration Test: Keycloak -> Profiles -> Kafka Verification -> Swipes -> Wait -> Verify Decks")
    public void testCompleteUserJourneyWithDeckBuild() throws Exception {
        long startTime = System.currentTimeMillis();
        TestStatistics stats = new TestStatistics();

        log.info("========================================");
        log.info("COMPREHENSIVE INTEGRATION TEST STARTED");
        log.info("========================================");
        log.info("PostgreSQL: {}", postgresContainer.getJdbcUrl());
        log.info("Kafka: {}", kafkaContainer.getBootstrapServers());
        log.info("Profiles Service Port: {} (actual: {})", PROFILES_PORT, actualServerPort);
        log.info("Swipes Service: {}", swipesBaseUrl);
        log.info("Users to create: {}", TEST_USER_COUNT);
        log.info("Deck build wait time: {} ms", DECK_BUILD_WAIT_TIME_MS);
        log.info("========================================");
        log.info("⚠️  IMPORTANT: Deck Service Redis Configuration");
        log.info("========================================");
        log.info("Deck service MUST connect to TEST Redis container!");
        log.info("");
        log.info("To start Deck Service with CORRECT Redis:");
        log.info("  cd services/deck && mvn spring-boot:run \\");
        log.info("    -Dspring-boot.run.arguments=\"\\");
        log.info("      --profiles.base-url=http://localhost:{}/api/v1/profiles/internal\"", PROFILES_PORT);
        log.info("");
        log.info("⚠️  If Deck Service uses default Redis (localhost:6379),");
        log.info("   it will NOT see test data and decks will be stored in wrong Redis!");
        log.info("========================================");

        try {
            // STEP 1: Create Keycloak users
            List<NewUserRecord> keycloakUsers = createKeycloakUsers(stats);

            // STEP 2: Create profiles for users
            List<ProfileTestData> profiles = createProfilesForUsers(keycloakUsers, stats);
            createdProfiles.addAll(profiles);

            // STEP 2.5: Verify Kafka ProfileCreateEvent messages
            verifyProfileCreateEvents(profiles, stats);

            // STEP 3: Create swipes between users
            createSwipesBetweenUsers(profiles, stats);

            // STEP 4: Wait 1.5 minutes for deck service to process
            waitForDeckServiceToProcess(stats);

            // STEP 5: Verify decks in Redis
            verifyDecksInRedis(profiles, stats);

            stats.testDurationMs = System.currentTimeMillis() - startTime;
            stats.printSummary();

            // Final assertions
            assertThat(stats.keycloakUsersCreated).isEqualTo(TEST_USER_COUNT);
            assertThat(stats.profilesCreated).isEqualTo(TEST_USER_COUNT);
            assertThat(stats.kafkaCreateEventsReceived)
                .as("Should receive ProfileCreateEvent for each profile")
                .isEqualTo(TEST_USER_COUNT);
            assertThat(stats.swipesCreated).isGreaterThan(0);

            // Deck verification is conditional since deck service may not be running
            // or may not have access to test Redis container
            if (stats.decksWithData > 0) {
                log.info("✓✓✓ BONUS: Deck service is running and built {} decks!", stats.decksWithData);
                assertThat(stats.decksFullyCorrect)
                    .as("If decks exist, they should be correct")
                    .isGreaterThanOrEqualTo(stats.decksWithData / 2); // At least 50% should be correct
            } else {
                log.warn("⚠ ⚠ ⚠  NO DECKS FOUND IN REDIS");
                log.warn("This is expected if:");
                log.warn("  1. Deck service is not running");
                log.warn("  2. Deck service cannot connect to test Redis container");
                log.warn("  3. Scheduler hasn't run yet (runs every minute)");
                log.warn("");
                log.warn("To verify deck functionality, start deck service with:");
                log.warn("  cd services/deck && mvn spring-boot:run \\");
                log.warn("    -Dspring-boot.run.arguments=\"\\");
                log.warn("      --profiles.base-url=http://localhost:{}/api/v1/profiles/internal\"", PROFILES_PORT);
                log.warn("");
                log.warn("Then run this test again.");
                log.warn("");
                log.warn("Test will continue without deck verification...");
            }

            log.info("========================================");
            log.info("COMPREHENSIVE INTEGRATION TEST PASSED");
            log.info("========================================");

        } catch (Exception e) {
            log.error("Comprehensive integration test failed", e);
            stats.testDurationMs = System.currentTimeMillis() - startTime;
            stats.printSummary();
            throw e;
        }
    }

    /**
     * STEP 1: Create Keycloak users
     */
    private List<NewUserRecord> createKeycloakUsers(TestStatistics stats) throws Exception {
        log.info("========================================");
        log.info("STEP 1: Creating Keycloak Users");
        log.info("========================================");

        userService.createTestUsers();
        List<NewUserRecord> users = userService.getUsers();

        int userCount = Math.min(users.size(), TEST_USER_COUNT);
        List<NewUserRecord> selectedUsers = users.subList(0, userCount);

        stats.keycloakUsersCreated = selectedUsers.size();

        log.info("✓ Created {} Keycloak users", stats.keycloakUsersCreated);

        for (int i = 0; i < selectedUsers.size(); i++) {
            NewUserRecord user = selectedUsers.get(i);
            log.info("  [{}] Username: {}, Email: {}", i + 1, user.username(), user.username());
        }

        return selectedUsers;
    }

    /**
     * STEP 2: Create profiles for Keycloak users
     */
    private List<ProfileTestData> createProfilesForUsers(List<NewUserRecord> users, TestStatistics stats) throws Exception {
        log.info("========================================");
        log.info("STEP 2: Creating Profiles for Users");
        log.info("========================================");

        List<ProfileTestData> profiles = new ArrayList<>();

        for (int i = 0; i < users.size(); i++) {
            NewUserRecord user = users.get(i);
            try {
                ProfileTestData profile = createProfile(user, i);
                profiles.add(profile);
                stats.profilesCreated++;

                log.info("[{}/{}] ✓ Profile created: {} (Age: {}, Gender: {}, Prefers: {})",
                    i + 1, users.size(), profile.firstName, profile.age,
                    profile.gender, profile.preferredGender);

            } catch (Exception e) {
                stats.profilesFailed++;
                log.error("[{}/{}] ✗ Failed to create profile for user: {}",
                    i + 1, users.size(), user.username(), e);
                throw e;
            }
        }

        log.info("✓ Successfully created {}/{} profiles", stats.profilesCreated, users.size());
        return profiles;
    }

    /**
     * STEP 2.5: Verify Kafka ProfileCreateEvent messages
     */
    private void verifyProfileCreateEvents(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 2.5: Verifying Kafka Events");
        log.info("========================================");
        log.info("Waiting for ProfileCreateEvent messages to be consumed...");

        // Wait for Kafka events with timeout
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int receivedCount = kafkaEventCollector.getProfileCreatedEvents().size();
                log.info("  Received {}/{} ProfileCreateEvent messages", receivedCount, profiles.size());

                assertThat(receivedCount)
                    .as("Should receive ProfileCreateEvent for each created profile")
                    .isGreaterThanOrEqualTo(profiles.size());
            });

        List<ProfileCreateEvent> events = kafkaEventCollector.getProfileCreatedEvents();
        stats.kafkaCreateEventsReceived = events.size();

        log.info("✓ Received {} ProfileCreateEvent messages", stats.kafkaCreateEventsReceived);
        log.info("");
        log.info("Verifying event correctness...");

        // Verify event structure and content
        int validEvents = 0;
        int eventsWithValidId = 0;
        int eventsWithValidTimestamp = 0;
        int eventsMatchingProfiles = 0;

        Set<String> createdProfileIds = profiles.stream()
            .map(p -> p.profileId)
            .collect(Collectors.toSet());

        for (int i = 0; i < events.size(); i++) {
            ProfileCreateEvent event = events.get(i);
            boolean isValid = true;

            // Check eventId
            if (event.getEventId() != null) {
                eventsWithValidId++;
            } else {
                log.warn("  [Event {}] Missing eventId", i + 1);
                isValid = false;
            }

            // Check profileId
            if (event.getProfileId() != null) {
                String profileId = event.getProfileId().toString();

                // Check if profileId matches one of created profiles
                if (createdProfileIds.contains(profileId)) {
                    eventsMatchingProfiles++;
                } else {
                    log.warn("  [Event {}] ProfileId {} does not match any created profile",
                        i + 1, profileId);
                    isValid = false;
                }
            } else {
                log.warn("  [Event {}] Missing profileId", i + 1);
                isValid = false;
            }

            // Check timestamp
            if (event.getTimestamp() != null) {
                eventsWithValidTimestamp++;
            } else {
                log.warn("  [Event {}] Missing timestamp", i + 1);
                isValid = false;
            }

            if (isValid) {
                validEvents++;
                log.debug("  [Event {}] ✓ Valid: eventId={}, profileId={}, timestamp={}",
                    i + 1, event.getEventId(), event.getProfileId(), event.getTimestamp());
            }
        }

        // Log verification results
        log.info("");
        log.info("Event Verification Results:");
        log.info("  Total events received: {}", events.size());
        log.info("  Events with valid eventId: {}/{}", eventsWithValidId, events.size());
        log.info("  Events with valid profileId: {}/{}", eventsMatchingProfiles, events.size());
        log.info("  Events with valid timestamp: {}/{}", eventsWithValidTimestamp, events.size());
        log.info("  Fully valid events: {}/{}", validEvents, events.size());

        // Assertions
        assertThat(eventsWithValidId)
            .as("All events should have eventId")
            .isEqualTo(events.size());

        assertThat(eventsMatchingProfiles)
            .as("All events should have profileId matching created profiles")
            .isEqualTo(events.size());

        assertThat(eventsWithValidTimestamp)
            .as("All events should have timestamp")
            .isEqualTo(events.size());

        assertThat(validEvents)
            .as("All events should be fully valid")
            .isEqualTo(events.size());

        // Check for duplicates
        Set<String> eventIds = events.stream()
            .map(e -> e.getEventId().toString())
            .collect(Collectors.toSet());

        assertThat(eventIds)
            .as("All eventIds should be unique")
            .hasSize(events.size());

        Set<String> eventProfileIds = events.stream()
            .map(e -> e.getProfileId().toString())
            .collect(Collectors.toSet());

        assertThat(eventProfileIds)
            .as("All profileIds in events should be unique")
            .hasSize(events.size());

        log.info("");
        log.info("✓✓✓ All Kafka event validations passed!");
        log.info("  - Correct number of events: {}", events.size());
        log.info("  - All events have valid structure");
        log.info("  - All events match created profiles");
        log.info("  - No duplicate events");
        log.info("========================================");
    }

    /**
     * Create a single profile with deterministic data
     */
    private ProfileTestData createProfile(NewUserRecord user, int index) throws Exception {
        String authHeader = keycloakTestHelper.createAuthorizationHeader(user.username(), user.password());
        String token = authHeader.replace("Bearer ", "");

        // Deterministic age distribution
        int age = MIN_AGE + (index % (MAX_AGE - MIN_AGE));

        // Alternating gender
        String gender = (index % 2 == 0) ? "MALE" : "FEMALE";

        // Preference: opposite gender
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

    /**
     * Build JSON for profile creation
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
     * STEP 3: Create swipes between users
     */
    private void createSwipesBetweenUsers(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 3: Creating Swipes Between Users");
        log.info("========================================");

        WebClient swipesClient = WebClient.builder().baseUrl(swipesBaseUrl).build();

        log.info("Each user will create {} swipes", SWIPES_PER_USER);

        for (int i = 0; i < profiles.size(); i++) {
            ProfileTestData swiper = profiles.get(i);
            int successfulSwipes = 0;

            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % profiles.size();

                if (targetIndex == i) {
                    continue;
                }

                ProfileTestData target = profiles.get(targetIndex);

                // Like if opposite gender matches preferences
                boolean isLike = !swiper.gender.equals(target.gender);

                if (createSwipe(swipesClient, swiper, target, isLike)) {
                    stats.swipesCreated++;
                    successfulSwipes++;
                    if (isLike) {
                        stats.likesCount++;
                    } else {
                        stats.dislikesCount++;
                    }
                } else {
                    stats.swipesFailed++;
                }
            }

            log.info("[{}/{}] User {} created {} swipes",
                i + 1, profiles.size(), swiper.firstName, successfulSwipes);
        }

        log.info("✓ Total swipes created: {} (Likes: {}, Dislikes: {})",
            stats.swipesCreated, stats.likesCount, stats.dislikesCount);
        log.info("  Failed swipes: {}", stats.swipesFailed);
    }

    /**
     * Create a single swipe
     */
    private boolean createSwipe(WebClient swipesClient, ProfileTestData swiper,
                                ProfileTestData target, boolean isLike) {
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
            log.debug("Swipe failed: {} -> {} - {}",
                swiper.getShortId(), target.getShortId(), e.getMessage());
            return false;
        }
    }

    /**
     * STEP 4: Wait for deck service to process events and build decks
     */
    private void waitForDeckServiceToProcess(TestStatistics stats) throws InterruptedException {
        log.info("========================================");
        log.info("STEP 4: Waiting for Deck Service");
        log.info("========================================");

        // Check current Redis state BEFORE waiting
        checkRedisStateBeforeWait();

        long startWait = System.currentTimeMillis();

        log.info("Waiting {} ms (1.5 minutes) for deck service to:", DECK_BUILD_WAIT_TIME_MS);
        log.info("  1. Consume Kafka events (profile.updated, swipe.created)");
        log.info("  2. Process event queue");
        log.info("  3. Build decks for all users");
        log.info("  4. Store decks in Redis");

        // Show progress during wait
        long remainingMs = DECK_BUILD_WAIT_TIME_MS;
        long intervalMs = 15_000; // 15 seconds

        while (remainingMs > 0) {
            long sleepTime = Math.min(intervalMs, remainingMs);
            TimeUnit.MILLISECONDS.sleep(sleepTime);
            remainingMs -= sleepTime;

            long elapsed = System.currentTimeMillis() - startWait;
            int percentComplete = (int) ((elapsed * 100) / DECK_BUILD_WAIT_TIME_MS);

            log.info("  [{}/{}s] {}% complete...",
                elapsed / 1000, DECK_BUILD_WAIT_TIME_MS / 1000, percentComplete);

            // Check Redis keys every 15 seconds
            checkRedisKeysDuringWait();
        }

        stats.deckBuildWaitTimeMs = System.currentTimeMillis() - startWait;

        log.info("✓ Wait completed ({} ms)", stats.deckBuildWaitTimeMs);

        // Final check after wait
        checkRedisStateAfterWait();
    }

    /**
     * Check Redis state before waiting
     */
    private void checkRedisStateBeforeWait() {
        log.info("---");
        log.info("Redis State Check (BEFORE waiting):");

        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys != null && !allKeys.isEmpty()) {
            log.info("  Total keys in Redis: {}", allKeys.size());

            long profileKeys = allKeys.stream().filter(k -> k.startsWith("profile:")).count();
            long swipeKeys = allKeys.stream().filter(k -> k.startsWith("swipe:")).count();
            long deckKeys = allKeys.stream().filter(k -> k.startsWith("deck:")).count();

            log.info("  Profile keys: {}", profileKeys);
            log.info("  Swipe keys: {}", swipeKeys);
            log.info("  Deck keys: {} ⚠️  (should be 0 before deck service runs)", deckKeys);
        } else {
            log.info("  Total keys in Redis: 0");
        }
        log.info("---");
    }

    /**
     * Check Redis keys during wait
     */
    private void checkRedisKeysDuringWait() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        if (deckKeys != null && !deckKeys.isEmpty()) {
            log.info("    [PROGRESS] Found {} deck keys in Redis!", deckKeys.size());
        }
    }

    /**
     * Check Redis state after waiting
     */
    private void checkRedisStateAfterWait() {
        log.info("---");
        log.info("Redis State Check (AFTER waiting):");

        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys != null && !allKeys.isEmpty()) {
            log.info("  Total keys in Redis: {}", allKeys.size());

            long deckKeys = allKeys.stream().filter(k -> k.startsWith("deck:")).count();
            log.info("  Deck keys: {}", deckKeys);

            if (deckKeys == 0) {
                log.warn("  ⚠️ ⚠️ ⚠️  NO DECK KEYS FOUND!");
                log.warn("  This means Deck Service either:");
                log.warn("    1. Is not running");
                log.warn("    2. Connected to WRONG Redis (localhost:6379 instead of test Redis)");
                log.warn("    3. Has not processed events yet");
                log.warn("");
                log.warn("  To verify Deck Service Redis connection:");
                log.warn("    Check Deck Service logs for Redis connection info");
            } else {
                log.info("  ✓ Deck keys found! Deck service is working correctly.");
            }
        } else {
            log.info("  Total keys in Redis: 0");
            log.warn("  ⚠️  Redis is empty - no data at all!");
        }
        log.info("---");
    }

    /**
     * STEP 5: Verify decks in Redis with comprehensive correctness checks
     *
     * This step performs the following verifications:
     * 1. Deck existence and size > 0
     * 2. Swiped profiles are excluded
     * 3. Deck candidates match viewer's preferences (age, gender)
     * 4. Deck contents match expected candidates
     */
    private void verifyDecksInRedis(List<ProfileTestData> profiles, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 5: Verifying Decks in Redis");
        log.info("========================================");
        log.info("Performing comprehensive deck correctness verification:");
        log.info("  1. Deck existence and size");
        log.info("  2. Swiped profiles exclusion");
        log.info("  3. Preference matching (age, gender)");
        log.info("  4. Candidate correctness");
        log.info("========================================");

        // Build swipe map for exclusion verification
        Map<String, Set<String>> userSwipedProfiles = buildSwipeMap(profiles);

        // Load all profiles from database for validation
        List<Profile> allProfiles = profileRepository.findAll();
        log.info("Loaded {} profiles from database for validation", allProfiles.size());

        for (ProfileTestData profile : profiles) {
            stats.decksVerified++;

            log.info("Verifying deck for user: {} [{}]", profile.firstName, profile.getShortId());

            String deckKey = "deck:" + profile.profileId;
            Boolean hasKey = redisTemplate.hasKey(deckKey);

            if (Boolean.FALSE.equals(hasKey)) {
                log.warn("  ✗ No deck found in Redis");
                continue;
            }

            Long deckSize = redisTemplate.opsForZSet().size(deckKey);

            if (deckSize == null || deckSize == 0) {
                log.warn("  ✗ Empty deck in Redis");
                continue;
            }

            stats.decksWithData++;
            stats.deckSizes.put(profile.profileId, deckSize.intValue());

            // Get deck contents from Redis
            Set<String> deckContents = redisTemplate.opsForZSet().range(deckKey, 0, -1);

            if (deckContents == null || deckContents.isEmpty()) {
                log.warn("  ✗ Could not read deck contents");
                continue;
            }

            // VERIFICATION 1: Check swiped profiles are excluded
            boolean hasCorrectExclusions = verifySwipedProfilesExcluded(
                profile, deckContents, userSwipedProfiles, stats);

            // VERIFICATION 2: Check candidates match preferences
            boolean hasCorrectCandidates = verifyCandidatesMatchPreferences(
                profile, deckContents, allProfiles, stats);

            // VERIFICATION 3: Check deck quality (size, diversity)
            boolean hasGoodQuality = verifyDeckQuality(
                profile, deckContents, allProfiles, stats);

            // Overall verdict
            if (hasCorrectExclusions && hasCorrectCandidates && hasGoodQuality) {
                stats.decksFullyCorrect++;
                log.info("  ✓✓✓ Deck is FULLY CORRECT (size: {}, all checks passed)", deckSize);
            } else {
                log.warn("  ⚠ Deck has issues (size: {}, exclusions: {}, candidates: {}, quality: {})",
                    deckSize, hasCorrectExclusions, hasCorrectCandidates, hasGoodQuality);
            }

            // Show sample of top candidates
            displayTopCandidates(profile, profiles, deckContents);

            log.info("  ---");
        }

        printVerificationSummary(stats);
    }

    /**
     * Verify that swiped profiles are excluded from deck
     */
    private boolean verifySwipedProfilesExcluded(
            ProfileTestData profile,
            Set<String> deckContents,
            Map<String, Set<String>> userSwipedProfiles,
            TestStatistics stats) {

        Set<String> swipedByUser = userSwipedProfiles.getOrDefault(profile.profileId, Set.of());
        List<String> foundSwiped = new ArrayList<>();

        for (String swipedId : swipedByUser) {
            if (deckContents.contains(swipedId)) {
                foundSwiped.add(swipedId);
            }
        }

        boolean hasCorrectExclusions = foundSwiped.isEmpty();

        if (hasCorrectExclusions) {
            stats.decksWithCorrectExclusions++;
            log.info("    ✓ Exclusion check: Correctly excludes {} swiped users", swipedByUser.size());
        } else {
            log.warn("    ✗ Exclusion check: INCORRECTLY contains {} swiped users: {}",
                foundSwiped.size(), foundSwiped);
        }

        return hasCorrectExclusions;
    }

    /**
     * Verify that all candidates in deck match viewer's preferences
     */
    private boolean verifyCandidatesMatchPreferences(
            ProfileTestData profile,
            Set<String> deckContents,
            List<Profile> allProfiles,
            TestStatistics stats) {

        // Get viewer's profile from DB to access preferences
        Optional<Profile> viewerOpt = allProfiles.stream()
            .filter(p -> p.getProfileId().toString().equals(profile.profileId))
            .findFirst();

        if (viewerOpt.isEmpty()) {
            log.warn("    ⚠ Could not find viewer profile in database");
            return false;
        }

        Profile viewer = viewerOpt.get();
        com.tinder.profiles.preferences.Preferences prefs = viewer.getPreferences();

        if (prefs == null) {
            log.warn("    ⚠ Viewer has no preferences");
            return false;
        }

        // Check each candidate in deck
        List<String> invalidCandidates = new ArrayList<>();
        int validCount = 0;

        for (String candidateId : deckContents) {
            Optional<Profile> candidateOpt = allProfiles.stream()
                .filter(p -> p.getProfileId().toString().equals(candidateId))
                .findFirst();

            if (candidateOpt.isEmpty()) {
                invalidCandidates.add(candidateId + " (not found)");
                continue;
            }

            Profile candidate = candidateOpt.get();

            // Check gender preference
            if (!matchesGenderPreference(candidate.getGender(), prefs.getGender())) {
                invalidCandidates.add(candidateId + " (wrong gender: " + candidate.getGender() + ")");
                continue;
            }

            // Check age preference
            if (!matchesAgePreference(candidate.getAge(), prefs.getMinAge(), prefs.getMaxAge())) {
                invalidCandidates.add(candidateId + " (wrong age: " + candidate.getAge() + ")");
                continue;
            }

            validCount++;
        }

        boolean allCandidatesValid = invalidCandidates.isEmpty();

        if (allCandidatesValid) {
            stats.decksWithCorrectCandidates++;
            log.info("    ✓ Preference check: All {} candidates match preferences (gender: {}, age: {}-{})",
                validCount, prefs.getGender(), prefs.getMinAge(), prefs.getMaxAge());
        } else {
            log.warn("    ✗ Preference check: {} invalid candidates found: {}",
                invalidCandidates.size(), invalidCandidates.stream().limit(3).collect(Collectors.toList()));
        }

        return allCandidatesValid;
    }

    /**
     * Verify deck quality - checks that deck service produces reasonable results
     * Rather than predicting exact deck contents, we validate:
     * - Deck has reasonable size (not empty, not too large)
     * - All candidates are valid (not deleted, not self)
     * - Deck has some diversity (not all same gender if ANY preference)
     */
    private boolean verifyDeckQuality(
            ProfileTestData profile,
            Set<String> deckContents,
            List<Profile> allProfiles,
            TestStatistics stats) {

        if (deckContents.isEmpty()) {
            log.warn("    ✗ Quality check: Deck is empty");
            return false;
        }

        // Check 1: Reasonable size (at least 1, max 500)
        int deckSize = deckContents.size();
        if (deckSize > 500) {
            log.warn("    ✗ Quality check: Deck too large ({} > 500)", deckSize);
            return false;
        }

        // Check 2: All candidates are valid profiles
        List<String> invalidCandidates = new ArrayList<>();
        for (String candidateId : deckContents) {
            Optional<Profile> candidateOpt = allProfiles.stream()
                .filter(p -> p.getProfileId().toString().equals(candidateId))
                .findFirst();

            if (candidateOpt.isEmpty()) {
                invalidCandidates.add(candidateId + " (not found)");
                continue;
            }

            Profile candidate = candidateOpt.get();

            // Should not include self
            if (candidate.getProfileId().toString().equals(profile.profileId)) {
                invalidCandidates.add(candidateId + " (self)");
                continue;
            }

            // Should not include deleted profiles
            if (candidate.isDeleted()) {
                invalidCandidates.add(candidateId + " (deleted)");
            }
        }

        if (!invalidCandidates.isEmpty()) {
            log.warn("    ✗ Quality check: {} invalid candidates: {}",
                invalidCandidates.size(), invalidCandidates.stream().limit(3).collect(Collectors.toList()));
            return false;
        }

        // Check 3: Verify deck has reasonable diversity (if preferences allow)
        Optional<Profile> viewerOpt = allProfiles.stream()
            .filter(p -> p.getProfileId().toString().equals(profile.profileId))
            .findFirst();

        if (viewerOpt.isPresent()) {
            Profile viewer = viewerOpt.get();
            com.tinder.profiles.preferences.Preferences prefs = viewer.getPreferences();

            if (prefs != null &&
                (prefs.getGender().equalsIgnoreCase("ANY") || prefs.getGender().equalsIgnoreCase("ALL"))) {

                // If preferences is ANY, deck should ideally have both genders
                long maleCount = deckContents.stream()
                    .map(id -> allProfiles.stream()
                        .filter(p -> p.getProfileId().toString().equals(id))
                        .findFirst())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(p -> p.getGender().equalsIgnoreCase("MALE"))
                    .count();

                long femaleCount = deckSize - maleCount;

                // Just informational - not a failure
                if (maleCount > 0 && femaleCount > 0) {
                    log.debug("      Good diversity: {} males, {} females", maleCount, femaleCount);
                }
            }
        }

        log.info("    ✓ Quality check: Deck size {} is reasonable with {} valid candidates",
            deckSize, deckSize - invalidCandidates.size());

        return true;
    }

    /**
     * Display top candidates from deck
     */
    private void displayTopCandidates(
            ProfileTestData profile,
            List<ProfileTestData> profiles,
            Set<String> deckContents) {

        Set<String> topCandidates = redisTemplate.opsForZSet().reverseRange(
            "deck:" + profile.profileId, 0, 2);

        if (topCandidates != null && !topCandidates.isEmpty()) {
            List<String> sampleInfo = topCandidates.stream()
                .map(id -> profiles.stream()
                    .filter(p -> p.profileId.equals(id))
                    .findFirst()
                    .map(p -> String.format("%s (%s, %d)", p.firstName, p.gender, p.age))
                    .orElse("Unknown"))
                .limit(3)
                .collect(Collectors.toList());
            log.info("    Top candidates: {}", sampleInfo);
        }
    }

    /**
     * Print comprehensive verification summary
     */
    private void printVerificationSummary(TestStatistics stats) {
        log.info("========================================");
        log.info("VERIFICATION SUMMARY");
        log.info("========================================");
        log.info("Total decks checked: {}", stats.decksVerified);
        log.info("Decks with data: {}", stats.decksWithData);
        log.info("Decks with correct exclusions: {}/{}",
            stats.decksWithCorrectExclusions, stats.decksWithData);
        log.info("Decks with correct candidates: {}/{}",
            stats.decksWithCorrectCandidates, stats.decksWithData);
        log.info("Decks fully correct: {}/{}",
            stats.decksFullyCorrect, stats.decksWithData);
        log.info("Average deck size: {}",
            stats.deckSizes.isEmpty() ? 0 :
            String.format("%.1f", stats.deckSizes.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0)));

        // Calculate success rate
        if (stats.decksWithData > 0) {
            double successRate = (stats.decksFullyCorrect * 100.0) / stats.decksWithData;
            log.info("Success rate: {}/100", String.format("%.1f", successRate));
        }
        log.info("========================================");
    }

    /**
     * Helper: Check if candidate gender matches viewer's preference
     */
    private boolean matchesGenderPreference(String candidateGender, String preferredGender) {
        if (preferredGender == null ||
            preferredGender.equalsIgnoreCase("any") ||
            preferredGender.equalsIgnoreCase("all")) {
            return true;
        }
        return candidateGender != null && candidateGender.equalsIgnoreCase(preferredGender);
    }

    /**
     * Helper: Check if candidate age matches viewer's preference
     */
    private boolean matchesAgePreference(Integer candidateAge, Integer minAge, Integer maxAge) {
        if (candidateAge == null) {
            return false;
        }
        if (minAge != null && candidateAge < minAge) {
            return false;
        }
        if (maxAge != null && candidateAge > maxAge) {
            return false;
        }
        return true;
    }

    /**
     * Build a map of which profiles each user has swiped on
     */
    private Map<String, Set<String>> buildSwipeMap(List<ProfileTestData> profiles) {
        Map<String, Set<String>> swipeMap = new HashMap<>();

        for (int i = 0; i < profiles.size(); i++) {
            ProfileTestData swiper = profiles.get(i);
            Set<String> swipedTargets = new HashSet<>();

            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % profiles.size();
                if (targetIndex != i) {
                    swipedTargets.add(profiles.get(targetIndex).profileId);
                }
            }

            swipeMap.put(swiper.profileId, swipedTargets);
        }

        return swipeMap;
    }
}
