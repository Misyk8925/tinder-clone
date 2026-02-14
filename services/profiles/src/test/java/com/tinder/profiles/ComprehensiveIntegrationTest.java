package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    // Use docker-compose Kafka instead of Testcontainers
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";

    static {
        log.info("Using docker-compose Kafka: {}", KAFKA_BOOTSTRAP_SERVERS);

        // Start PostgreSQL
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

        // Kafka - use docker-compose Kafka (same as Deck service)
        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.producer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> KAFKA_BOOTSTRAP_SERVERS);

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
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TestKafkaConsumerConfig.TestKafkaEventCollector kafkaEventCollector;

    private final KeycloakTestHelper keycloakTestHelper = new KeycloakTestHelper();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${swipes.base-url:http://localhost:8040}")
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
        Set<String> jwtCacheKeys = redisTemplate.keys("jwt:cache:*");
        if (jwtCacheKeys != null && !jwtCacheKeys.isEmpty()) {
            redisTemplate.delete(jwtCacheKeys);
        }

        // Clean up database
        profileRepository.deleteAll();
        preferencesRepository.deleteAll();

        createdProfiles.clear();

        log.info("Test setup complete: Redis and database cleaned");
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
        int decksPartiallyCorrect = 0;
        Map<String, Integer> deckSizes = new LinkedHashMap<>();
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
            log.info("Decks partially correct: {}", decksPartiallyCorrect);
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
        log.info("Kafka: {} (docker-compose)", KAFKA_BOOTSTRAP_SERVERS);
        log.info("Profiles Service Port: {} (actual: {})", PROFILES_PORT, actualServerPort);
        log.info("Swipes Service: {}", swipesBaseUrl);
        log.info("Users to create: {}", TEST_USER_COUNT);
        log.info("Deck build wait time: {} ms", DECK_BUILD_WAIT_TIME_MS);
        log.info("========================================");

        try {
            // STEP 1: Create Keycloak users
            List<NewUserRecord> keycloakUsers = createKeycloakUsers(stats);

            // Remember event count BEFORE creating profiles
            int initialCreateEventCount = kafkaEventCollector.getProfileCreatedEvents().size();
            log.info("Events in collector before profile creation: {}", initialCreateEventCount);

            // STEP 2: Create profiles for users
            List<ProfileTestData> profiles = createProfilesForUsers(keycloakUsers, stats);
            createdProfiles.addAll(profiles);

            // STEP 2.5: Verify Kafka ProfileCreateEvent messages
            verifyProfileCreateEvents(profiles, initialCreateEventCount, stats);

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
            assertThat(stats.kafkaUpdateEventsReceived)
                .as("Should receive ProfileUpdatedEvent for updated profiles")
                .isGreaterThan(0);
            assertThat(stats.swipesCreated).isGreaterThan(0);

            // Deck verification is conditional since deck service may not be running
            if (stats.decksWithData > 0) {
                log.info("✓ Deck service is running and built {} decks!", stats.decksWithData);

                double successRate = (stats.decksFullyCorrect * 100.0) / stats.decksWithData;
                log.info("Deck quality: {}% fully correct ({}/{})",
                    String.format("%.1f", successRate), stats.decksFullyCorrect, stats.decksWithData);

                int acceptableDecks = stats.decksFullyCorrect + stats.decksPartiallyCorrect;
                assertThat(acceptableDecks)
                    .as("At least some decks should be fully or partially correct")
                    .isGreaterThan(0);
            } else {
                log.warn("⚠ NO DECKS FOUND - Deck service may not be running or connected to test environment");
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
    private void verifyProfileCreateEvents(List<ProfileTestData> profiles, int initialEventCount, TestStatistics stats) {
        log.info("========================================");
        log.info("STEP 2.5: Verifying Kafka Events");
        log.info("========================================");

        // initialEventCount is passed as parameter (counted BEFORE profile creation)
        log.info("ProfileCreateEvents before creation: {}", initialEventCount);
        log.info("Expected new events: {}", profiles.size());
        log.info("Waiting for ProfileCreateEvent messages to be consumed...");

        // Wait for NEW events (increment) with timeout
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int currentCount = kafkaEventCollector.getProfileCreatedEvents().size();
                int newEventsReceived = currentCount - initialEventCount;
                log.info("  Received {}/{} ProfileCreateEvent messages (new since start: {})",
                    currentCount, profiles.size(), newEventsReceived);

                assertThat(newEventsReceived)
                    .as("Should receive ProfileCreateEvent for each created profile")
                    .isGreaterThanOrEqualTo(profiles.size());
            });

        int finalEventCount = kafkaEventCollector.getProfileCreatedEvents().size();
        int newEventsReceived = finalEventCount - initialEventCount;

        // Get profileIds of created profiles in this test
        Set<String> createdProfileIds = profiles.stream()
            .map(p -> p.profileId)
            .collect(Collectors.toSet());

        // Filter only events that belong to profiles created in THIS test
        List<ProfileCreateEvent> allEvents = kafkaEventCollector.getProfileCreatedEvents();
        List<ProfileCreateEvent> events = allEvents.stream()
            .filter(event -> event.getProfileId() != null &&
                           createdProfileIds.contains(event.getProfileId().toString()))
            .collect(Collectors.toList());

        // Record filtered count for statistics (not the raw increment)
        stats.kafkaCreateEventsReceived = events.size();

        log.info("✓ Received {} ProfileCreateEvent messages (total: {}, filtered for this test: {})",
            newEventsReceived, finalEventCount, events.size());

        // Verify event structure and content
        int validEvents = 0;
        int eventsWithValidId = 0;
        int eventsWithValidTimestamp = 0;
        int eventsMatchingProfiles = 0;

        for (int i = 0; i < events.size(); i++) {
            ProfileCreateEvent event = events.get(i);
            boolean isValid = true;

            if (event.getEventId() != null) {
                eventsWithValidId++;
            } else {
                log.warn("  [Event {}] Missing eventId", i + 1);
                isValid = false;
            }

            if (event.getProfileId() != null) {
                eventsMatchingProfiles++;
            } else {
                log.warn("  [Event {}] Missing profileId", i + 1);
                isValid = false;
            }

            if (event.getTimestamp() != null) {
                eventsWithValidTimestamp++;
            } else {
                log.warn("  [Event {}] Missing timestamp", i + 1);
                isValid = false;
            }

            if (isValid) {
                validEvents++;
            }
        }

        log.info("Event Verification: {}/{} valid events", validEvents, events.size());

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

        assertThat(events.size())
            .as("Should receive event for EACH created profile")
            .isEqualTo(profiles.size());

        log.info("✓ All Kafka event validations passed");
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
     * STEP 3.5: Update some profiles and verify Kafka ProfileUpdatedEvent messages
     */
    private void updateProfilesAndVerifyEvents(TestStatistics stats) throws Exception {
        log.info("========================================");
        log.info("STEP 3.5: Updating Profiles (Mid-Wait)");
        log.info("========================================");

        int maxUpdates = Math.min(15, createdProfiles.size());
        int locationUpdatesTarget = Math.min(2, maxUpdates);
        int profilesToUpdate = maxUpdates - locationUpdatesTarget;
        log.info("Will update {} profiles out of {} to test all ChangeTypes (plus {} location changes)",
                profilesToUpdate, createdProfiles.size(), locationUpdatesTarget);

        // Reset Kafka event collector to count only new update events
        int beforeUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
        log.info("ProfileUpdatedEvents before update: {}", beforeUpdateEvents);

        int profilesUpdated = 0;
        int preferencesUpdates = 0;
        int criticalFieldsUpdates = 0;
        int nonCriticalUpdates = 0;
        int locationUpdates = 0;
        Set<UUID> locationUpdatedProfileIds = new HashSet<>();

        for (int i = 0; i < profilesToUpdate; i++) {
            ProfileTestData profile = createdProfiles.get(i);

            try {
                String patchJson;
                String updateType;

                // Test baseline update types
                int updateTypeIndex = i % 3;

                if (updateTypeIndex == 0) {
                    // Test PREFERENCES change
                    patchJson = String.format("""
                        {
                            "preferences": {
                                "minAge": %d,
                                "maxAge": %d,
                                "gender": "%s",
                                "maxRange": %d
                            }
                        }""",
                        MIN_AGE + 2,  // Change minAge
                        MAX_AGE + 5,  // Change maxAge
                        "all",        // Change gender preference
                        DEFAULT_MAX_RANGE + 10  // Change maxRange
                    );
                    updateType = "PREFERENCES";
                    preferencesUpdates++;

                } else if (updateTypeIndex == 1) {
                    // Test CRITICAL_FIELDS change (age is critical field)
                    patchJson = String.format("""
                        {
                            "age": %d,
                            "bio": "Updated bio at %d seconds - Integration test update"
                        }""",
                        profile.age + 1,  // Increment age by 1
                        System.currentTimeMillis() / 1000
                    );
                    updateType = "CRITICAL_FIELDS";
                    criticalFieldsUpdates++;

                } else {
                    // Test NON_CRITICAL change (only bio and name)
                    patchJson = String.format("""
                        {
                            "name": "%s",
                            "bio": "Non-critical update at %d seconds - Integration test"
                        }""",
                        profile.firstName + " Updated",
                        System.currentTimeMillis() / 1000
                    );
                    updateType = "NON_CRITICAL";
                    nonCriticalUpdates++;
                }

                mockMvc.perform(patch("")
                                .content(patchJson)
                                .header("Authorization", "Bearer " + profile.token)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());

                profilesUpdated++;
                log.info("[{}/{}] ✓ Updated profile: {} (ChangeType: {})",
                        i + 1, maxUpdates, profile.firstName, updateType);

            } catch (Exception e) {
                log.error("[{}/{}] ✗ Failed to update profile: {}",
                        i + 1, maxUpdates, profile.firstName, e);
            }
        }

        for (int i = 0; i < locationUpdatesTarget; i++) {
            ProfileTestData profile = createdProfiles.get(profilesToUpdate + i);

            try {
                // City validation disallows digits; keep test values simple and deterministic.
                String newCity = (i % 2 == 0) ? "Munich" : "Hamburg";
                String patchJson = String.format("""
                        {
                            "city": "%s"
                        }""", newCity);

                mockMvc.perform(patch("")
                                .content(patchJson)
                                .header("Authorization", "Bearer " + profile.token)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());

                profilesUpdated++;
                locationUpdates++;
                locationUpdatedProfileIds.add(UUID.fromString(profile.profileId));
                log.info("[{}/{}] ✓ Updated profile: {} (ChangeType: LOCATION_CHANGE)",
                        profilesToUpdate + i + 1, maxUpdates, profile.firstName);

            } catch (Exception e) {
                log.error("[{}/{}] ✗ Failed to update profile: {}",
                        profilesToUpdate + i + 1, maxUpdates, profile.firstName, e);
            }
        }

        log.info("✓ Successfully updated {}/{} profiles", profilesUpdated, maxUpdates);
        log.info("  Updates by type - PREFERENCES: {}, CRITICAL_FIELDS: {}, NON_CRITICAL: {}, LOCATION_CHANGE: {}",
            preferencesUpdates, criticalFieldsUpdates, nonCriticalUpdates, locationUpdates);

        // Wait for Kafka events with timeout
        log.info("Waiting for ProfileUpdatedEvent messages...");

        final int expectedNewEvents = profilesUpdated;
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int currentUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
                int newEvents = currentUpdateEvents - beforeUpdateEvents;

                log.info("  Received {}/{} ProfileUpdatedEvent messages (new since update)",
                        newEvents, expectedNewEvents);

                assertThat(newEvents)
                    .as("Should receive ProfileUpdatedEvent for each updated profile")
                    .isGreaterThanOrEqualTo(expectedNewEvents);
            });

        int afterUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
        int newEvents = afterUpdateEvents - beforeUpdateEvents;
        stats.kafkaUpdateEventsReceived = newEvents;

        log.info("✓ Received {} new ProfileUpdatedEvent messages", newEvents);
        log.info("");
        log.info("Verifying ProfileUpdatedEvent correctness...");

        // Verify the new update events
        List<com.tinder.profiles.kafka.dto.ProfileUpdatedEvent> updateEvents =
            kafkaEventCollector.getProfileUpdatedEvents()
                .subList(beforeUpdateEvents, afterUpdateEvents);

        int validEvents = 0;
        int preferencesEvents = 0;
        int criticalFieldsEvents = 0;
        int nonCriticalEvents = 0;
        int locationChangeEvents = 0;
        Set<UUID> locationChangeProfileIds = new HashSet<>();

        for (int i = 0; i < updateEvents.size(); i++) {
            com.tinder.profiles.kafka.dto.ProfileUpdatedEvent event = updateEvents.get(i);
            boolean isValid = true;

            // Check eventId
            if (event.getEventId() == null) {
                log.warn("  [Event {}] Missing eventId", i + 1);
                isValid = false;
            }

            // Check profileId
            if (event.getProfileId() == null) {
                log.warn("  [Event {}] Missing profileId", i + 1);
                isValid = false;
            }

            // Check changeType and count each type
            if (event.getChangeType() == null) {
                log.warn("  [Event {}] Missing changeType", i + 1);
                isValid = false;
            } else {
                switch (event.getChangeType()) {
                    case PREFERENCES -> preferencesEvents++;
                    case CRITICAL_FIELDS -> criticalFieldsEvents++;
                    case NON_CRITICAL -> nonCriticalEvents++;
                    case LOCATION_CHANGE -> {
                        locationChangeEvents++;
                        if (event.getProfileId() != null) {
                            locationChangeProfileIds.add(event.getProfileId());
                        }
                    }
                }
            }

            // Check changedFields
            if (event.getChangedFields() == null || event.getChangedFields().isEmpty()) {
                log.warn("  [Event {}] Missing or empty changedFields", i + 1);
                isValid = false;
            } else {
                // Verify expected fields based on changeType
                if (event.getChangeType() != null) {
                    switch (event.getChangeType()) {
                        case PREFERENCES -> {
                            if (!event.getChangedFields().contains("preferences")) {
                                log.warn("  [Event {}] PREFERENCES changeType should contain 'preferences' field: {}",
                                        i + 1, event.getChangedFields());
                            }
                        }
                        case CRITICAL_FIELDS -> {
                            boolean hasCriticalField = event.getChangedFields().stream()
                                    .anyMatch(field -> field.equals("age") || field.equals("gender"));
                            if (!hasCriticalField) {
                                log.warn("  [Event {}] CRITICAL_FIELDS changeType should contain at least one critical field (age/gender): {}",
                                        i + 1, event.getChangedFields());
                            }
                        }
                        case NON_CRITICAL -> {
                            boolean hasOnlyNonCritical = event.getChangedFields().stream()
                                    .noneMatch(field -> field.equals("age") || field.equals("gender") ||
                                               field.equals("city") || field.equals("preferences"));
                            if (!hasOnlyNonCritical) {
                                log.warn("  [Event {}] NON_CRITICAL changeType should only contain non-critical fields (name/bio): {}",
                                        i + 1, event.getChangedFields());
                            }
                        }
                        case LOCATION_CHANGE -> {
                            if (!event.getChangedFields().contains("city")) {
                                log.warn("  [Event {}] LOCATION_CHANGE changeType should contain 'city' field: {}",
                                        i + 1, event.getChangedFields());
                            }
                        }
                    }
                }
            }

            // Check timestamp
            if (event.getTimestamp() == null) {
                log.warn("  [Event {}] Missing timestamp", i + 1);
                isValid = false;
            }

            if (isValid) {
                validEvents++;
                log.debug("  [Event {}] ✓ Valid: eventId={}, profileId={}, changeType={}, fields={}",
                        i + 1, event.getEventId(), event.getProfileId(),
                        event.getChangeType(), event.getChangedFields());
            }
        }

        log.info("");
        log.info("ProfileUpdatedEvent Verification Results:");
        log.info("  Total new events: {}", newEvents);
        log.info("  Fully valid events: {}/{}", validEvents, newEvents);
        log.info("  ChangeType distribution:");
        log.info("    - PREFERENCES: {}", preferencesEvents);
        log.info("    - CRITICAL_FIELDS: {}", criticalFieldsEvents);
        log.info("    - NON_CRITICAL: {}", nonCriticalEvents);
        log.info("    - LOCATION_CHANGE: {}", locationChangeEvents);

        // Assertions
        assertThat(validEvents)
            .as("All ProfileUpdatedEvent should be valid")
            .isEqualTo(newEvents);

        // Verify we have all change event types
        assertThat(preferencesEvents)
            .as("Should have PREFERENCES change events")
            .isGreaterThan(0);

        assertThat(criticalFieldsEvents)
            .as("Should have CRITICAL_FIELDS change events")
            .isGreaterThan(0);

        assertThat(nonCriticalEvents)
            .as("Should have NON_CRITICAL change events")
            .isGreaterThan(0);

        assertThat(locationChangeEvents)
            .as("Should have LOCATION_CHANGE events")
            .isGreaterThanOrEqualTo(locationUpdates);

        assertThat(locationChangeProfileIds)
            .as("Should have LOCATION_CHANGE events for all profiles patched with city")
            .containsAll(locationUpdatedProfileIds);

        log.info("✓ All ProfileUpdatedEvent validations passed");
        log.info("========================================");
    }

    /**
     * STEP 3.6: Delete some profiles and verify Kafka ProfileDeleteEvent messages
     */
    private void deleteProfilesAndVerifyEvents(TestStatistics stats) throws Exception {
        log.info("========================================");
        log.info("STEP 3.6: Deleting Profiles (Mid-Wait)");
        log.info("========================================");

        int profilesToDelete = Math.min(5, createdProfiles.size());
        // Delete profiles from the end of the list to avoid disrupting main swipes
        int startIndex = Math.max(0, createdProfiles.size() - profilesToDelete);

        log.info("Will delete {} profiles out of {} (from index {} to end) to test ProfileDeleteEvent",
                profilesToDelete, createdProfiles.size(), startIndex);

        // Reset Kafka event collector to count only new delete events
        int beforeDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        log.info("ProfileDeletedEvents before delete: {}", beforeDeleteEvents);

        int profilesDeleted = 0;
        List<ProfileTestData> deletedProfiles = new ArrayList<>();

        for (int i = startIndex; i < createdProfiles.size(); i++) {
            ProfileTestData profile = createdProfiles.get(i);

            try {
                mockMvc.perform(MockMvcRequestBuilders.delete("")
                                .header("Authorization", "Bearer " + profile.token))
                        .andExpect(status().isNoContent());

                profilesDeleted++;
                deletedProfiles.add(profile);
                log.info("[{}/{}] ✓ Deleted profile: {}", profilesDeleted, profilesToDelete, profile.firstName);

            } catch (Exception e) {
                log.error("[{}/{}] ✗ Failed to delete profile: {}",
                        profilesDeleted + 1, profilesToDelete, profile.firstName, e);
            }
        }

        log.info("✓ Successfully deleted {}/{} profiles", profilesDeleted, profilesToDelete);

        // Wait for Kafka events with timeout
        log.info("Waiting for ProfileDeleteEvent messages...");

        final int expectedNewEvents = profilesDeleted;
        await()
            .atMost(30, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(2, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int currentDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
                int newEvents = currentDeleteEvents - beforeDeleteEvents;

                log.info("  Received {}/{} ProfileDeleteEvent messages (new since delete)",
                        newEvents, expectedNewEvents);

                assertThat(newEvents)
                    .as("Should receive ProfileDeleteEvent for each deleted profile")
                    .isGreaterThanOrEqualTo(expectedNewEvents);
            });

        int afterDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        int newEvents = afterDeleteEvents - beforeDeleteEvents;
        stats.kafkaDeleteEventsReceived = newEvents;

        log.info("✓ Received {} new ProfileDeleteEvent messages", newEvents);
        log.info("");
        log.info("Verifying ProfileDeleteEvent correctness...");

        // Verify the new delete events
        List<com.tinder.profiles.kafka.dto.ProfileDeleteEvent> deleteEvents =
            kafkaEventCollector.getProfileDeletedEvents()
                .subList(beforeDeleteEvents, afterDeleteEvents);

        int validEvents = 0;
        Set<UUID> deletedProfileIds = deletedProfiles.stream()
                .map(p -> UUID.fromString(p.profileId))
                .collect(Collectors.toSet());

        for (int i = 0; i < deleteEvents.size(); i++) {
            com.tinder.profiles.kafka.dto.ProfileDeleteEvent event = deleteEvents.get(i);
            boolean isValid = true;

            // Check eventId
            if (event.getEventId() == null) {
                log.warn("  [Event {}] Missing eventId", i + 1);
                isValid = false;
            }

            // Check profileId
            if (event.getProfileId() == null) {
                log.warn("  [Event {}] Missing profileId", i + 1);
                isValid = false;
            } else {
                // Verify profileId is one of the deleted profiles
                if (!deletedProfileIds.contains(event.getProfileId())) {
                    log.warn("  [Event {}] ProfileId {} is not one of the deleted profiles",
                            i + 1, event.getProfileId());
                    isValid = false;
                }
            }

            // Check timestamp
            if (event.getTimestamp() == null) {
                log.warn("  [Event {}] Missing timestamp", i + 1);
                isValid = false;
            }

            if (isValid) {
                validEvents++;
                log.debug("  [Event {}] ✓ Valid: eventId={}, profileId={}, timestamp={}",
                        i + 1, event.getEventId(), event.getProfileId(), event.getTimestamp());
            }
        }

        log.info("");
        log.info("ProfileDeleteEvent Verification Results:");
        log.info("  Total new events: {}", newEvents);
        log.info("  Fully valid events: {}/{}", validEvents, newEvents);

        // Assertions
        assertThat(validEvents)
            .as("All ProfileDeleteEvent should be valid")
            .isEqualTo(newEvents);

        assertThat(validEvents)
            .as("Should have received delete events for all deleted profiles")
            .isEqualTo(profilesDeleted);

        log.info("✓ All ProfileDeleteEvent validations passed");
        log.info("========================================");
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
                    .uri("/api/v1/swipes")
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
    private void waitForDeckServiceToProcess(TestStatistics stats) throws Exception {
        log.info("========================================");
        log.info("STEP 4: Waiting for Deck Service");
        log.info("========================================");

        checkRedisStateBeforeWait();

        long startWait = System.currentTimeMillis();
        log.info("Waiting {} seconds for deck service to process events",
            DECK_BUILD_WAIT_TIME_MS / 1000);

        // Show progress during wait
        long remainingMs = DECK_BUILD_WAIT_TIME_MS;
        long intervalMs = 15_000;
        boolean profilesUpdated = false;
        boolean profilesDeleted = false;

        while (remainingMs > 0) {
            long sleepTime = Math.min(intervalMs, remainingMs);
            TimeUnit.MILLISECONDS.sleep(sleepTime);
            remainingMs -= sleepTime;

            long elapsed = System.currentTimeMillis() - startWait;
            log.info("  Wait progress: {}/{}s", elapsed / 1000, DECK_BUILD_WAIT_TIME_MS / 1000);

            // After 45 seconds, update some profiles
            if (!profilesUpdated && elapsed >= 45_000) {
                updateProfilesAndVerifyEvents(stats);
                profilesUpdated = true;
            }

            // After 55 seconds, delete some profiles
            if (!profilesDeleted && elapsed >= 55_000) {
                deleteProfilesAndVerifyEvents(stats);
                profilesDeleted = true;
            }

            checkRedisKeysDuringWait();
        }

        stats.deckBuildWaitTimeMs = System.currentTimeMillis() - startWait;
        log.info("✓ Wait completed ({} ms)", stats.deckBuildWaitTimeMs);
        checkRedisStateAfterWait();
    }

    /**
     * Check Redis state before waiting
     */
    private void checkRedisStateBeforeWait() {
        Set<String> allKeys = redisTemplate.keys("*");
        if (allKeys != null && !allKeys.isEmpty()) {
            List<String> sortedKeys = new ArrayList<>(allKeys);
            Collections.sort(sortedKeys);

            long deckKeys = sortedKeys.stream().filter(k -> k.startsWith("deck:")).count();
            log.info("Redis state: {} total keys, {} deck keys (should be 0 initially)",
                sortedKeys.size(), deckKeys);
        }
    }

    /**
     * Check Redis keys during wait
     */
    private void checkRedisKeysDuringWait() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        if (deckKeys != null && !deckKeys.isEmpty()) {
            log.info("    [PROGRESS] Found {} deck keys in Redis", deckKeys.size());
        }
    }

    /**
     * Check Redis state after waiting
     */
    private void checkRedisStateAfterWait() {
        Set<String> deckKeys = redisTemplate.keys("deck:*");
        long deckKeyCount = (deckKeys != null) ? deckKeys.size() : 0;

        if (deckKeyCount == 0) {
            log.warn("No deck keys found - Deck service may not be running");
        } else {
            log.info("✓ Found {} deck keys in Redis", deckKeyCount);
        }
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

        // Build swipe map for exclusion verification
        Map<String, Set<String>> userSwipedProfiles = buildSwipeMap(profiles);

        // Load all profiles from database for validation
        List<Profile> allProfiles = profileRepository.findAll();
        // Sort profiles by UUID for deterministic order
        allProfiles.sort(Comparator.comparing(p -> p.getProfileId().toString()));
        log.info("Loaded {} profiles from database for validation", allProfiles.size());

        // Sort profiles list for deterministic iteration
        List<ProfileTestData> sortedProfiles = new ArrayList<>(profiles);
        sortedProfiles.sort(Comparator.comparing(p -> p.profileId));

        for (ProfileTestData profile : sortedProfiles) {
            stats.decksVerified++;

            log.info("Verifying deck for user: {} [{}]", profile.firstName, profile.getShortId());

            String deckKey = "deck:" + profile.profileId;
            Boolean hasKey = redisTemplate.hasKey(deckKey);

            if (hasKey == null || !hasKey) {
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
            Set<String> deckContentsSet = redisTemplate.opsForZSet().range(deckKey, 0, -1);

            if (deckContentsSet == null || deckContentsSet.isEmpty()) {
                log.warn("  ✗ Could not read deck contents");
                continue;
            }

            // Convert to sorted list for deterministic iteration
            List<String> deckContents = new ArrayList<>(deckContentsSet);
            Collections.sort(deckContents);

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
                log.info("  ✓ Deck is FULLY CORRECT (size: {})", deckSize);
            } else {
                if (hasCorrectExclusions || hasCorrectCandidates) {
                    stats.decksPartiallyCorrect++;
                }
            }

            // Show sample of top candidates
            displayTopCandidates(profile, profiles, deckContents);
        }

        printVerificationSummary(stats);
    }

    /**
     * Verify that swiped profiles are excluded from deck
     */
    private boolean verifySwipedProfilesExcluded(
            ProfileTestData profile,
            List<String> deckContents,
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
            log.warn("    ✗ Exclusion check: {} swiped profiles found in deck (should be excluded)",
                foundSwiped.size());
        }

        return hasCorrectExclusions;
    }

    /**
     * Verify that all candidates in deck match viewer's preferences
     */
    private boolean verifyCandidatesMatchPreferences(
            ProfileTestData profile,
            List<String> deckContents,
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
            log.info("    ✓ Preference check: All {} candidates match preferences", validCount);
        } else {
            log.warn("    ✗ Preference check: {} invalid candidates", invalidCandidates.size());
        }

        return allCandidatesValid;
    }

    /**
     * Verify deck quality - checks that deck service produces reasonable results
     */
    private boolean verifyDeckQuality(
            ProfileTestData profile,
            List<String> deckContents,
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
            log.warn("    ✗ Quality check: {} invalid candidates", invalidCandidates.size());
            return false;
        }

        log.info("    ✓ Quality check: Deck size {} is reasonable", deckSize);

        return true;
    }

    /**
     * Display top candidates from deck
     */
    private void displayTopCandidates(
            ProfileTestData profile,
            List<ProfileTestData> profiles,
            List<String> deckContents) {

        // Get top candidates from Redis (sorted by score)
        Set<String> topCandidatesSet = redisTemplate.opsForZSet().reverseRange(
            "deck:" + profile.profileId, 0, 2);

        if (topCandidatesSet != null && !topCandidatesSet.isEmpty()) {
            // Convert to list and sort for deterministic output
            List<String> topCandidates = new ArrayList<>(topCandidatesSet);

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
        return maxAge == null || candidateAge <= maxAge;
    }

    /**
     * Build a map of which profiles each user has swiped on
     */
    private Map<String, Set<String>> buildSwipeMap(List<ProfileTestData> profiles) {
        Map<String, Set<String>> swipeMap = new LinkedHashMap<>();

        for (int i = 0; i < profiles.size(); i++) {
            ProfileTestData swiper = profiles.get(i);
            Set<String> swipedTargets = new LinkedHashSet<>();

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
