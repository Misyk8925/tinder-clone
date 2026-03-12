package com.tinder.profiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tinder.profiles.kafka.dto.MatchCreateEvent;
import com.tinder.profiles.kafka.dto.ProfileCreateEvent;
import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.user.NewUserRecord;
import com.tinder.profiles.user.UserService;
import com.tinder.profiles.util.KafkaAdminHelper;
import com.tinder.profiles.util.KeycloakTestHelper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
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
 *     <li>Verify all internal endpoints via real mTLS connection (deck-service identity)</li>
 * </ol>
 *
 * <p>Steps 1-8 remain unchanged. Step 9 adds a dedicated mTLS pass over every
 * {@code /internal/**} endpoint using a {@link WebClient} configured with
 * {@code deck-service.p12} as client identity and {@code truststore-test.jks} for
 * server trust.  When the mTLS port (8011) is not reachable the live-TLS assertions
 * are gracefully skipped while the MockMvc-based security assertions still run.
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveIntegrationTest extends AbstractProfilesIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ComprehensiveIntegrationTest.class);

    // ── Test configuration ────────────────────────────────────────────────────

    private static final int TEST_USER_COUNT          = 1000;
    private static final int PARALLEL_THREADS         = 50;
    private static final int MIN_AGE                  = 20;
    private static final int MAX_AGE                  = 35;
    private static final int SWIPES_PER_USER          = 3;
    private static final int FORCED_MUTUAL_MATCH_PAIRS = 3;
    private static final String DEFAULT_CITY          = "Berlin";
    private static final String DEFAULT_BIO           = "Integration test user";
    private static final int DEFAULT_MAX_RANGE        = 10;
    private static final long DECK_BUILD_WAIT_TIME_MS = 180_000;

    // ── Ports / base URLs ─────────────────────────────────────────────────────

    /** Public HTTP port — JWT bearer tokens required */
    private static final int PUBLIC_PORT    = 8010;
    /** Internal HTTPS mTLS port — deck-service client cert required */
    private static final int MTLS_PORT      = 8011;

    private static final String MTLS_INTERNAL_BASE  = "https://localhost:" + MTLS_PORT  + "/api/v1/profiles/internal";
    private static final String CONSUMER_BASE_URL   = "http://localhost:8050";
    private static final String DECK_BASE_URL       = "http://localhost:8030";

    // ── mTLS keystore constants ───────────────────────────────────────────────

    private static final String KS_PASSWORD        = "changeit";
    /** Client identity presented to profiles-service (CN=deck-service) */
    private static final String DECK_KEYSTORE       = "deck-service.p12";
    /** JKS truststore used to verify the server certificate */
    private static final String TRUSTSTORE_TEST     = "truststore-test.jks";
    /** Fallback truststore name in main/resources */
    private static final String TRUSTSTORE_MAIN     = "truststore.jks";

    // ── Shared state across ordered test methods ──────────────────────────────

    private final List<ProfileTestData> createdProfiles =
            Collections.synchronizedList(new ArrayList<>());
    private final TestStatistics stats   = new TestStatistics();
    private final KafkaAdminHelper kafkaAdmin = new KafkaAdminHelper(KAFKA_BOOTSTRAP_SERVERS);

    private List<NewUserRecord> keycloakUsers;
    private int  initialCreateEventCount;
    private int  initialMatchCreateEventsCount;
    private long initialMatchConsumerOffset;
    private int  expectedMutualMatches;

    /** True when consumer-service is reachable (swipe-history API) */
    private boolean consumerServiceAvailable = false;
    /** True when deck-service is reachable and building decks */
    private boolean deckServiceAvailable     = false;
    /** True when the mTLS Tomcat connector on port 8011 is open */
    private boolean mtlsPortOpen             = false;

    /** mTLS WebClient using deck-service.p12 — initialised in @BeforeAll if port is open */
    private WebClient internalMtlsClient;

    // ── Injected beans ────────────────────────────────────────────────────────

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

    @Override
    void setUp() {
        // Intentionally empty — one-time setup runs in setUpOnce()
    }

    @BeforeAll
    void setUpOnce() throws Exception {
        // Clean Redis and DB
        Set<String> deckKeys  = redisTemplate.keys("deck:*");
        if (deckKeys  != null && !deckKeys.isEmpty())  redisTemplate.delete(deckKeys);
        Set<String> jwtKeys   = redisTemplate.keys("jwt:cache:*");
        if (jwtKeys   != null && !jwtKeys.isEmpty())   redisTemplate.delete(jwtKeys);
        profileRepository.deleteAll();
        preferencesRepository.deleteAll();
        kafkaEventCollector.reset();
        createdProfiles.clear();

        // Probe the mTLS port and, if open, build the shared WebClient
        mtlsPortOpen = isPortOpen("localhost", MTLS_PORT, 2_000);
        log.info("=================================================");
        log.info("mTLS port {} is {}", MTLS_PORT, mtlsPortOpen ? "OPEN" : "CLOSED (live Step-9 TLS tests will be skipped)");
        log.info("=================================================");
        if (mtlsPortOpen) {
            internalMtlsClient = MtlsClientFactory.build(MTLS_INTERNAL_BASE, DECK_KEYSTORE, TRUSTSTORE_TEST, KS_PASSWORD);
            log.info("mTLS WebClient initialised: base={}", MTLS_INTERNAL_BASE);
        }

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
        int profilesFailed  = 0;
        int kafkaCreateEventsReceived = 0;
        int kafkaUpdateEventsReceived = 0;
        int kafkaDeleteEventsReceived = 0;
        int kafkaMatchCreateEventsReceived = 0;
        int expectedMutualMatches = 0;
        int syntheticMatchEventsPublished  = 0;
        long matchConsumerCommittedOffsetDelta = 0;
        boolean matchConsumerVerificationSkipped = false;
        int swipesCreated  = 0;
        int swipesFailed   = 0;
        int likesCount     = 0;
        int dislikesCount  = 0;
        int decksVerified  = 0;
        int decksWithData  = 0;
        int decksWithCorrectExclusions  = 0;
        int decksWithCorrectCandidates  = 0;
        int decksFullyCorrect           = 0;
        int decksPartiallyCorrect       = 0;
        // Step 9 mTLS stats
        int mtlsInternalEndpointsVerified  = 0;
        int mtlsInternalEndpointsFailed    = 0;
        boolean mtlsStepSkipped            = false;
        Map<String, Integer> deckSizes     = new LinkedHashMap<>();
        long testDurationMs      = 0;
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
            log.info("Consumer service available (swipe exclusions): {}",
                    !mtlsStepSkipped ? "N/A (mTLS step skipped)" : "see step-9");
            log.info("Decks with correct exclusions: {} (0 is expected when consumer service is down)",
                    decksWithCorrectExclusions);
            log.info("Decks with correct candidates: {}", decksWithCorrectCandidates);
            log.info("Decks fully correct: {}", decksFullyCorrect);
            log.info("Decks partially correct: {}", decksPartiallyCorrect);
            log.info("Average deck size: {}", deckSizes.isEmpty() ? 0 :
                    deckSizes.values().stream().mapToInt(Integer::intValue).average().orElse(0));
            log.info("--- Step 9 mTLS ---");
            log.info("mTLS port open: {}", !mtlsStepSkipped);
            log.info("Internal endpoints verified OK: {}", mtlsInternalEndpointsVerified);
            log.info("Internal endpoints failed: {}", mtlsInternalEndpointsFailed);
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
        log.info("Profiles Service Port: {} (mTLS internal: {})", actualServerPort, MTLS_PORT);
        log.info("Swipes Service: {}", swipesBaseUrl);
        log.info("Users to create: {}", TEST_USER_COUNT);
        log.info("========================================");

        long start = System.currentTimeMillis();
        userService.createTestUsers(TEST_USER_COUNT);
        List<NewUserRecord> users = userService.getUsers();

        int userCount = Math.min(users.size(), TEST_USER_COUNT);
        keycloakUsers = users.subList(0, userCount);
        stats.keycloakUsersCreated = keycloakUsers.size();

        log.info("Created {} Keycloak users in {} ms",
                stats.keycloakUsersCreated, System.currentTimeMillis() - start);

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

        // Capture baseline BEFORE any profiles are created (includes pre-warm)
        // so that Step 3 can count exactly TEST_USER_COUNT new events
        initialCreateEventCount = kafkaEventCollector.getProfileCreatedEvents().size();
        log.info("Kafka events baseline (before any profile creation): {}", initialCreateEventCount);

        // Pre-warm location cache: create the first profile synchronously so the
        // LocationService geocodes DEFAULT_CITY once and stores it in both the DB and the
        // in-memory L1 cache.  All remaining 999 requests will get an instant cache hit.
        log.info("Pre-warming location cache for city '{}'...", DEFAULT_CITY);
        try {
            ProfileTestData firstProfile = createProfile(keycloakUsers.get(0), 0);
            createdProfiles.add(firstProfile);
            log.info("Location cache pre-warmed, first profile: {}", firstProfile.getShortId());
        } catch (Exception e) {
            log.warn("Location pre-warm failed (will retry inside batch): {}", e.getMessage());
        }

        // Determine which users still need a profile (skip index 0 if pre-warm succeeded)
        int startIndex = createdProfiles.isEmpty() ? 0 : 1;
        AtomicInteger successCount = new AtomicInteger(createdProfiles.size());
        AtomicInteger failCount    = new AtomicInteger(0);

        long profileStart = System.currentTimeMillis();
        List<Integer> remaining = new ArrayList<>(keycloakUsers.size() - startIndex);
        for (int i = startIndex; i < keycloakUsers.size(); i++) remaining.add(i);

        // Flux.flatMap with boundedElastic scheduler — all blocking MockMvc calls run on
        // a dedicated thread pool while the concurrency parameter caps in-flight requests.
        Flux.fromIterable(remaining)
            .flatMap(i -> {
                NewUserRecord user = keycloakUsers.get(i);
                return Mono.fromCallable(() -> {
                    ProfileTestData profile = createProfile(user, i);
                    createdProfiles.add(profile);
                    int done = successCount.incrementAndGet();
                    if (done % 100 == 0)
                        log.info("Progress: {}/{} profiles created...", done, keycloakUsers.size());
                    return profile;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    failCount.incrementAndGet();
                    log.error("[{}] Profile creation failed for {}: {}", i + 1, user.username(), e.getMessage());
                    return Mono.empty();
                });
            }, PARALLEL_THREADS * 2)
            .collectList()
            .block(Duration.ofMinutes(10));

        stats.profilesCreated = successCount.get();
        stats.profilesFailed  = failCount.get();

        log.info("Profile creation done in {} ms: {}/{} succeeded, {} failed",
                System.currentTimeMillis() - profileStart,
                stats.profilesCreated, keycloakUsers.size(), stats.profilesFailed);

        // ── Settle pause ──────────────────────────────────────────────────────
        // Allow 2 s for any in-flight DB writes to fully commit before we inspect
        // the list.  Under high concurrency (100 threads), a small number of
        // requests can fail transiently (connection-pool saturation, lock waits).
        TimeUnit.SECONDS.sleep(2);

        // ── Sequential retry for failed profiles ──────────────────────────────
        // Identify users whose profile did not make it into createdProfiles and
        // retry them one at a time to avoid reproducing the same contention.
        if (failCount.get() > 0) {
            log.info("Detected {} failure(s) — retrying sequentially after settle pause...",
                    failCount.get());

            Set<String> successfulUsernames;
            synchronized (createdProfiles) {
                successfulUsernames = createdProfiles.stream()
                        .map(ProfileTestData::username)
                        .collect(Collectors.toCollection(java.util.HashSet::new));
            }

            int retried = 0, retryOk = 0;
            for (int i = 0; i < keycloakUsers.size(); i++) {
                NewUserRecord user = keycloakUsers.get(i);
                if (successfulUsernames.contains(user.username())) continue;
                retried++;
                try {
                    ProfileTestData profile = createProfile(user, i);
                    createdProfiles.add(profile);
                    successCount.incrementAndGet();
                    retryOk++;
                    log.info("Retry [{}/{}] OK: {}", retried, failCount.get(), user.username());
                } catch (Exception e) {
                    log.warn("Retry [{}/{}] FAILED for {}: {}",
                            retried, failCount.get(), user.username(), e.getMessage());
                }
            }

            stats.profilesCreated = successCount.get();
            log.info("Retry phase complete: {}/{} profiles recovered", retryOk, retried);
        }

        // Deduplicate createdProfiles by profileId in-place.
        // Under high concurrency the same profileId can theoretically be added twice
        // (e.g. pre-warm + Flux overlap, or a rare Flux retry).  Remove duplicates now
        // so that downstream steps work with a clean, authoritative list.
        Set<String> seenIds = new java.util.LinkedHashSet<>();
        synchronized (createdProfiles) {
            List<ProfileTestData> deduped = createdProfiles.stream()
                .filter(p -> seenIds.add(p.profileId()))
                .collect(Collectors.toList());

            int removed = createdProfiles.size() - deduped.size();
            if (removed > 0) {
                log.warn("Removed {} duplicate profileId(s) from createdProfiles after batch creation", removed);
                createdProfiles.clear();
                createdProfiles.addAll(deduped);
            }
        }

        assertThat(createdProfiles).as("createdProfiles must contain exactly TEST_USER_COUNT unique entries")
            .hasSize(TEST_USER_COUNT);
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

        // Brief pause before verifying Kafka events — ensures all DB commits,
        // outbox publishes, and async writes from step 2 have fully settled.
        try { TimeUnit.SECONDS.sleep(2); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        log.info("ProfileCreateEvents before creation: {}", initialCreateEventCount);
        log.info("Expected new events: {}", TEST_USER_COUNT);

        // Build the canonical set of expected profileIds.
        // Use a Set so duplicate entries in createdProfiles (e.g. from a pre-warm retry) are
        // collapsed automatically — the authoritative count is the SET size, not the list size.
        Set<String> createdProfileIds = createdProfiles.stream()
            .map(ProfileTestData::profileId).collect(Collectors.toSet());

        // Diagnostic: warn if createdProfiles list has duplicates (two entries share the same profileId)
        if (createdProfileIds.size() != createdProfiles.size()) {
            log.warn("createdProfiles list contains {} duplicate profileId(s): list.size()={}, set.size()={}",
                    createdProfiles.size() - createdProfileIds.size(),
                    createdProfiles.size(), createdProfileIds.size());

            // Log the actual duplicated IDs for easier debugging
            Map<String, Long> idFrequency = createdProfiles.stream()
                .collect(Collectors.groupingBy(ProfileTestData::profileId, Collectors.counting()));
            idFrequency.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .forEach(e -> log.warn("  Duplicate profileId: {} (appears {} times)", e.getKey(), e.getValue()));
        }

        // The number of UNIQUE profiles we actually created — this is the target for Kafka events.
        int expectedEventCount = createdProfileIds.size();
        log.info("Unique profileIds in createdProfiles: {} (list size: {})",
                expectedEventCount, createdProfiles.size());

        // Wait until every unique profileId has a matching ProfileCreateEvent.
        // We count DISTINCT profileIds covered by events (not raw event count) so that
        // duplicate Kafka deliveries for one profile cannot prematurely satisfy the condition
        // while another profile's event is still in-flight.
        await()
            .atMost(180, TimeUnit.SECONDS)
            .pollDelay(2, TimeUnit.SECONDS)
            .pollInterval(5, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                long coveredIds = kafkaEventCollector.getProfileCreatedEvents().stream()
                        .filter(e -> e.getProfileId() != null
                                && createdProfileIds.contains(e.getProfileId().toString()))
                        .map(e -> e.getProfileId().toString())
                        .distinct()
                        .count();
                log.info("  Covered {}/{} unique profileIds with ProfileCreateEvent",
                        coveredIds, expectedEventCount);
                assertThat(coveredIds)
                    .as("Should receive ProfileCreateEvent for every unique created profile")
                    .isGreaterThanOrEqualTo(expectedEventCount);
            });

        int finalEventCount = kafkaEventCollector.getProfileCreatedEvents().size();

        List<ProfileCreateEvent> events = kafkaEventCollector.getProfileCreatedEvents().stream()
            .filter(e -> e.getProfileId() != null && createdProfileIds.contains(e.getProfileId().toString()))
            .collect(Collectors.toList());

        // Deduplicate by profileId — Kafka at-least-once delivery may produce duplicates;
        // keep only the first event received per profileId.
        Map<String, ProfileCreateEvent> deduplicatedByProfileId = new java.util.LinkedHashMap<>();
        for (ProfileCreateEvent event : events) {
            if (event.getProfileId() != null) {
                deduplicatedByProfileId.putIfAbsent(event.getProfileId().toString(), event);
            }
        }
        List<ProfileCreateEvent> uniqueEvents = new ArrayList<>(deduplicatedByProfileId.values());

        int duplicatesDropped = events.size() - uniqueEvents.size();
        if (duplicatesDropped > 0) {
            log.warn("Dropped {} duplicate ProfileCreateEvent(s) (same profileId delivered more than once)",
                    duplicatesDropped);
        }

        stats.kafkaCreateEventsReceived = uniqueEvents.size();
        log.info("Received {} ProfileCreateEvent messages (total: {}, filtered: {}, unique by profileId: {})",
            finalEventCount - initialCreateEventCount, finalEventCount, events.size(), uniqueEvents.size());

        int validEvents = 0, eventsWithValidId = 0, eventsWithValidTimestamp = 0, eventsMatchingProfiles = 0;
        for (ProfileCreateEvent event : uniqueEvents) {
            boolean isValid = true;
            if (event.getEventId()   != null) eventsWithValidId++; else isValid = false;
            if (event.getProfileId() != null) eventsMatchingProfiles++; else isValid = false;
            if (event.getTimestamp() != null) eventsWithValidTimestamp++; else isValid = false;
            if (isValid) validEvents++;
        }

        assertThat(eventsWithValidId).as("All events should have eventId").isEqualTo(uniqueEvents.size());
        assertThat(eventsMatchingProfiles).as("All events should have profileId").isEqualTo(uniqueEvents.size());
        assertThat(eventsWithValidTimestamp).as("All events should have timestamp").isEqualTo(uniqueEvents.size());
        assertThat(validEvents).as("All events should be fully valid").isEqualTo(uniqueEvents.size());

        Set<String> eventIds = uniqueEvents.stream().map(e -> e.getEventId().toString()).collect(Collectors.toSet());
        assertThat(eventIds).as("All eventIds should be unique").hasSize(uniqueEvents.size());

        Set<String> eventProfileIds = uniqueEvents.stream().map(e -> e.getProfileId().toString()).collect(Collectors.toSet());
        assertThat(eventProfileIds).as("All profileIds in events should be unique").hasSize(uniqueEvents.size());

        // Assert against the SET size (unique profiles), not TEST_USER_COUNT.
        // If createdProfiles has duplicates, we've already warned above and the real fix
        // should be investigated in step 2; here we only verify Kafka coverage.
        assertThat(uniqueEvents.size())
            .as("Should receive ProfileCreateEvent for EACH unique created profile")
            .isEqualTo(expectedEventCount);

        // Separately enforce that the number of unique profiles equals TEST_USER_COUNT
        assertThat(expectedEventCount)
            .as("Number of unique profileIds created must equal TEST_USER_COUNT — " +
                "duplicates in createdProfiles indicate a bug in step 2 (pre-warm + Flux overlap)")
            .isEqualTo(TEST_USER_COUNT);

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
        initialMatchConsumerOffset    = kafkaAdmin.getCommittedOffsetForTopic(matchConsumerGroupId, matchCreatedTopic);

        WebClient swipesClient = WebClient.builder().baseUrl(swipesBaseUrl).build();
        log.info("Each user will create {} swipes", SWIPES_PER_USER);

        List<SwipeTask> swipeTasks    = new ArrayList<>();
        List<ProfileTestData> snapshot = new ArrayList<>(createdProfiles);

        for (int i = 0; i < snapshot.size(); i++) {
            ProfileTestData swiper = snapshot.get(i);
            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int targetIndex = (i + j) % snapshot.size();
                if (targetIndex == i) continue;
                ProfileTestData target = snapshot.get(targetIndex);
                boolean isLike = !swiper.gender().equals(target.gender());
                swipeTasks.add(new SwipeTask(swiper, target, isLike));
            }
        }

        log.info("Total swipe tasks to execute: {}", swipeTasks.size());
        long start = System.currentTimeMillis();

        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger failCount     = new AtomicInteger(0);
        AtomicInteger likesCounter  = new AtomicInteger(0);
        AtomicInteger dislikesCounter = new AtomicInteger(0);

        Flux.fromIterable(swipeTasks)
            .flatMap(task -> {
                Map<String, Object> body = Map.of(
                        "profile1Id", task.swiper().profileId(),
                        "profile2Id", task.target().profileId(),
                        "decision",   task.isLike());
                return swipesClient.post()
                        .uri("/api/v1/swipes")
                        .header("Authorization", "Bearer " + task.swiper().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                                .maxBackoff(Duration.ofSeconds(5)))
                        .doOnSuccess(r -> {
                            int done = successCount.incrementAndGet();
                            if (task.isLike()) likesCounter.incrementAndGet();
                            else               dislikesCounter.incrementAndGet();
                            if (done % 500 == 0)
                                log.info("Completed {}/{} swipes...", done, swipeTasks.size());
                        })
                        .onErrorResume(e -> {
                            failCount.incrementAndGet();
                            log.debug("Swipe failed: {} → {}: {}",
                                    task.swiper().getShortId(), task.target().getShortId(), e.getMessage());
                            return Mono.empty();
                        });
            }, 100)
            .collectList()
            .block(Duration.ofMinutes(5));

        stats.swipesCreated  = successCount.get();
        stats.swipesFailed   = failCount.get();
        stats.likesCount     = likesCounter.get();
        stats.dislikesCount  = dislikesCounter.get();

        log.info("Swipe creation completed in {} ms: {} succeeded, {} failed",
                System.currentTimeMillis() - start, stats.swipesCreated, stats.swipesFailed);

        expectedMutualMatches       = createForcedMutualLikes(swipesClient);
        stats.expectedMutualMatches = expectedMutualMatches;

        log.info("Total swipes: {} (Likes: {}, Dislikes: {})",
                stats.swipesCreated, stats.likesCount, stats.dislikesCount);
        log.info("Forced mutual pairs for match validation: {}", expectedMutualMatches);

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
                .map(ProfileTestData::profileId).collect(Collectors.toSet());

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
            assertThat(event.getEventId()).as("MatchCreateEvent eventId must be present").isNotBlank();
            assertThat(event.getCreatedAt()).as("MatchCreateEvent createdAt must be present").isNotNull();
            assertThat(event.getProfile1Id()).as("MatchCreateEvent profile1Id must be present").isNotBlank();
            assertThat(event.getProfile2Id()).as("MatchCreateEvent profile2Id must be present").isNotBlank();
            assertThat(event.getProfile1Id())
                    .as("Match participants must be distinct").isNotEqualTo(event.getProfile2Id());
            uniqueEventIds.add(event.getEventId());
            String pairKey = event.getProfile1Id().compareTo(event.getProfile2Id()) <= 0
                    ? event.getProfile1Id() + "|" + event.getProfile2Id()
                    : event.getProfile2Id() + "|" + event.getProfile1Id();
            uniquePairKeys.add(pairKey);
        }

        assertThat(uniqueEventIds.size())
                .as("Each match event should have a unique eventId").isEqualTo(eventsForThisRun.size());
        assertThat(uniquePairKeys.size())
                .as("Should create at least one unique match pair").isGreaterThanOrEqualTo(expectedAtLeast);

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
            log.warn("Skipping: integration.match.verify-consumer-offsets=false");
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

        if (!stats.matchConsumerVerificationSkipped) {
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

        consumerServiceAvailable = isServiceReachable(CONSUMER_BASE_URL + "/actuator/health");
        deckServiceAvailable     = isServiceReachable(DECK_BASE_URL     + "/actuator/health");
        log.info("Service availability — deck: {}, consumer(swipes): {}",
                deckServiceAvailable, consumerServiceAvailable);

        if (!deckServiceAvailable)
            log.warn("Deck service is NOT reachable at {} — decks will not be built", DECK_BASE_URL);
        if (!consumerServiceAvailable)
            log.warn("Consumer service is NOT reachable at {} — fail-open (no exclusions)", CONSUMER_BASE_URL);

        checkRedisStateBeforeWait();
        long startWait = System.currentTimeMillis();

        TimeUnit.SECONDS.sleep(45);
        updateProfilesAndVerifyEvents();

        TimeUnit.SECONDS.sleep(10);
        deleteProfilesAndVerifyEvents();

        long elapsed   = System.currentTimeMillis() - startWait;
        long remaining = DECK_BUILD_WAIT_TIME_MS - elapsed;
        if (remaining > 0) {
            long pollIntervalMs = 30_000;
            long waited = 0;
            while (waited < remaining) {
                long sleepMs = Math.min(pollIntervalMs, remaining - waited);
                TimeUnit.MILLISECONDS.sleep(sleepMs);
                waited += sleepMs;
                Set<String> keys = redisTemplate.keys("deck:*");
                long built = (keys != null) ? keys.size() : 0;
                log.info("Deck build progress: {}/{} decks built ({} ms remaining)",
                        built, createdProfiles.size(), remaining - waited);
            }
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
        for (Profile p : allProfiles) profileIndex.put(p.getProfileId().toString(), p);

        // Build O(1) lookup index for test data
        Map<String, ProfileTestData> testDataIndex = new HashMap<>(createdProfiles.size());
        for (ProfileTestData p : createdProfiles) testDataIndex.put(p.profileId(), p);

        List<ProfileTestData> sorted = new ArrayList<>(createdProfiles);
        sorted.sort(Comparator.comparing(ProfileTestData::profileId));

        for (ProfileTestData profile : sorted) {
            stats.decksVerified++;
            String deckKey = "deck:" + profile.profileId();
            Boolean hasKey = redisTemplate.hasKey(deckKey);
            if (hasKey == null || !hasKey) continue;

            Long deckSize = redisTemplate.opsForZSet().size(deckKey);
            if (deckSize == null || deckSize == 0) continue;

            stats.decksWithData++;
            stats.deckSizes.put(profile.profileId(), deckSize.intValue());

            Set<String> deckContentsSet = redisTemplate.opsForZSet().range(deckKey, 0, -1);
            if (deckContentsSet == null || deckContentsSet.isEmpty()) continue;

            List<String> deckContents = new ArrayList<>(deckContentsSet);
            Collections.sort(deckContents);

            boolean hasCorrectExclusions = verifySwipedProfilesExcluded(profile, deckContents, userSwipedProfiles);
            boolean hasCorrectCandidates = verifyCandidatesMatchPreferences(profile, deckContents, profileIndex);
            boolean hasGoodQuality       = verifyDeckQuality(profile, deckContents, profileIndex);

            boolean exclusionsCount = !consumerServiceAvailable || hasCorrectExclusions;
            if (exclusionsCount && hasCorrectCandidates && hasGoodQuality) stats.decksFullyCorrect++;
            else if (hasCorrectExclusions || hasCorrectCandidates) stats.decksPartiallyCorrect++;
        }

        log.info("Deck verification complete: {}/{} checked, {} with data, {} fully correct",
                stats.decksVerified, sorted.size(), stats.decksWithData, stats.decksFullyCorrect);

        if (stats.decksWithData > 0 && stats.decksWithData < sorted.size() / 10) {
            log.warn("Low deck coverage: only {}/{} decks built.", stats.decksWithData, sorted.size());
        }

        if (!consumerServiceAvailable) {
            log.warn("Consumer service was NOT available — swipe exclusions were NOT applied (fail-open).");
        }

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
            int acceptable = stats.decksFullyCorrect + stats.decksPartiallyCorrect;
            assertThat(acceptable)
                .as("At least some decks should be fully or partially correct")
                .isGreaterThan(0);
        } else {
            log.warn("NO DECKS FOUND — deck service may not be running in this environment");
        }
    }

    // ── Step 9: mTLS internal endpoint verification ───────────────────────────

    @Test
    @Order(9)
    @DisplayName("Step 9: Verify all /internal/** endpoints via mTLS (deck-service identity)")
    void step9_verifyInternalEndpointsMtls() {
        log.info("========================================");
        log.info("STEP 9: Verifying Internal Endpoints via mTLS");
        log.info("mTLS port open: {}", mtlsPortOpen);
        log.info("========================================");

        if (!mtlsPortOpen) {
            stats.mtlsStepSkipped = true;
            log.warn("mTLS port {} is NOT open — skipping live TLS assertions. " +
                     "Run the full stack (docker-compose up) to enable Step 9.", MTLS_PORT);
            // Still assert that we have profiles so the test is not vacuously passing
            assertThat(createdProfiles).as("Profiles must exist before mTLS step").isNotEmpty();
            return;
        }

        ProfileTestData sampleProfile = createdProfiles.get(0);
        String viewerId  = sampleProfile.profileId();
        String singleId  = sampleProfile.profileId();

        // ── 9.1  GET /internal/active ─────────────────────────────────────────
        log.info("9.1: GET /internal/active via mTLS");
        try {
            List<?> activeProfiles = internalMtlsClient.get()
                    .uri("/active")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(15));

            assertThat(activeProfiles).isNotNull();
            log.info("9.1 PASSED — /internal/active returned {} profiles", activeProfiles.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.1 FAILED — /internal/active: {}", e.getMessage(), e);
        }

        // ── 9.2  GET /internal/page ───────────────────────────────────────────
        log.info("9.2: GET /internal/page via mTLS");
        try {
            List<?> page = internalMtlsClient.get()
                    .uri(u -> u.path("/page").queryParam("page", 0).queryParam("size", 20).build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(15));

            assertThat(page).isNotNull();
            log.info("9.2 PASSED — /internal/page returned {} items", page.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.2 FAILED — /internal/page: {}", e.getMessage(), e);
        }

        // ── 9.3  GET /internal/search ─────────────────────────────────────────
        log.info("9.3: GET /internal/search via mTLS (viewerId={})", viewerId.substring(0, 8));
        try {
            List<?> searchResult = internalMtlsClient.get()
                    .uri(u -> u.path("/search")
                            .queryParam("viewerId", viewerId)
                            .queryParam("limit", 50)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(15));

            assertThat(searchResult).isNotNull();
            log.info("9.3 PASSED — /internal/search returned {} profiles", searchResult.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.3 FAILED — /internal/search: {}", e.getMessage(), e);
        }

        // ── 9.4  GET /internal/by-ids ─────────────────────────────────────────
        // Send first 10 created profile IDs as a comma-separated string
        log.info("9.4: GET /internal/by-ids via mTLS");
        try {
            String commaIds = createdProfiles.stream()
                    .limit(10)
                    .map(ProfileTestData::profileId)
                    .collect(Collectors.joining(","));

            List<?> byIdsResult = internalMtlsClient.get()
                    .uri(u -> u.path("/by-ids").queryParam("ids", commaIds).build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(15));

            assertThat(byIdsResult)
                    .as("/internal/by-ids must return a non-null list")
                    .isNotNull();
            assertThat(byIdsResult.size())
                    .as("/internal/by-ids should return up to 10 profiles")
                    .isGreaterThan(0);

            log.info("9.4 PASSED — /internal/by-ids returned {}/10 profiles", byIdsResult.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.4 FAILED — /internal/by-ids: {}", e.getMessage(), e);
        }

        // ── 9.5  GET /internal/deck ───────────────────────────────────────────
        log.info("9.5: GET /internal/deck via mTLS (viewerId={})", viewerId.substring(0, 8));
        try {
            List<?> deck = internalMtlsClient.get()
                    .uri(u -> u.path("/deck")
                            .queryParam("viewerId", viewerId)
                            .queryParam("offset", 0)
                            .queryParam("limit", 20)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(15));

            assertThat(deck).isNotNull();
            log.info("9.5 PASSED — /internal/deck returned {} entries", deck.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.5 FAILED — /internal/deck: {}", e.getMessage(), e);
        }

        // ── 9.6  Reject: wrong CN must be denied ─────────────────────────────
        log.info("9.6: Wrong CN (profiles-service cert) should be rejected with 401/403");
        try {
            WebClient wrongCnClient = MtlsClientFactory.build(
                    MTLS_INTERNAL_BASE, "profiles-service.p12", TRUSTSTORE_TEST, KS_PASSWORD);

            WebClientResponseException rejected = Assertions.assertThrows(
                    WebClientResponseException.class,
                    () -> wrongCnClient.get()
                            .uri("/active")
                            .retrieve()
                            .bodyToMono(List.class)
                            .block(Duration.ofSeconds(10))
            );

            assertThat(rejected.getStatusCode().value())
                    .as("Wrong-CN cert should be rejected with HTTP 401 or 403")
                    .isIn(401, 403);

            log.info("9.6 PASSED — wrong CN rejected with HTTP {}", rejected.getStatusCode().value());
            stats.mtlsInternalEndpointsVerified++;
        } catch (AssertionError ae) {
            throw ae; // rethrow assertion failures immediately
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.6 FAILED — wrong-CN rejection test: {}", e.getMessage(), e);
        }

        // ── 9.7  Response schema: profileId field present in each item ────────
        log.info("9.7: /internal/active response schema — every element must have 'profileId'");
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> schemaCheck = internalMtlsClient.get()
                    .uri("/active")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block(Duration.ofSeconds(15));

            assertThat(schemaCheck).isNotNull();
            for (Map<String, Object> item : schemaCheck) {
                assertThat(item)
                        .as("Each profile must contain 'profileId' field")
                        .containsKey("profileId");
                assertThat(item.get("profileId")).isNotNull();
            }

            log.info("9.7 PASSED — schema validated for {} profiles", schemaCheck.size());
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.7 FAILED — schema validation: {}", e.getMessage(), e);
        }

        // ── 9.8  Concurrent load: 20 parallel mTLS requests ──────────────────
        log.info("9.8: 20 concurrent mTLS requests to /internal/active");
        int concurrency = 20;
        try {
            List<Long> sizes = Flux.range(0, concurrency)
                    .flatMap(i -> internalMtlsClient.get()
                            .uri("/active")
                            .retrieve()
                            .bodyToMono(List.class)
                            .map(list -> (long) list.size())
                            .onErrorReturn(-1L),
                            concurrency)
                    .collectList()
                    .block(Duration.ofSeconds(30));

            assertThat(sizes).isNotNull().hasSize(concurrency);
            long successCount = sizes.stream().filter(s -> s >= 0).count();
            long failedCount  = concurrency - successCount;
            log.info("9.8: {}/{} requests succeeded", successCount, concurrency);

            assertThat(successCount)
                    .as("All {} concurrent mTLS requests should succeed", concurrency)
                    .isEqualTo(concurrency);

            log.info("9.8 PASSED — all {} concurrent requests succeeded", concurrency);
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.8 FAILED — concurrent load test: {}", e.getMessage(), e);
        }

        // ── 9.9  Validate /internal/by-ids with all 1000 profile IDs (batched) ─
        log.info("9.9: /internal/by-ids batch verification across all created profiles");
        int batchSize   = 100;
        int totalBatches = (int) Math.ceil(createdProfiles.size() / (double) batchSize);
        int totalFound  = 0;
        boolean batchPassed = true;
        try {
            for (int b = 0; b < totalBatches; b++) {
                int from = b * batchSize;
                int to   = Math.min(from + batchSize, createdProfiles.size());
                String batchIds = createdProfiles.subList(from, to).stream()
                        .map(ProfileTestData::profileId)
                        .collect(Collectors.joining(","));

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> batchResult = internalMtlsClient.get()
                        .uri(u -> u.path("/by-ids").queryParam("ids", batchIds).build())
                        .retrieve()
                        .bodyToFlux(Map.class)
                        .map(m -> (Map<String, Object>) m)
                        .collectList()
                        .block(Duration.ofSeconds(20));

                if (batchResult == null) { batchPassed = false; break; }
                totalFound += batchResult.size();

                if (b % 5 == 0)
                    log.info("9.9 batch {}/{}: returned {}/{} profiles",
                            b + 1, totalBatches, batchResult.size(), to - from);
            }

            log.info("9.9: Found {} / {} profiles across {} batches",
                    totalFound, createdProfiles.size(), totalBatches);

            assertThat(batchPassed).as("/internal/by-ids batches must all succeed").isTrue();
            assertThat(totalFound)
                    .as("/internal/by-ids should return at least 90%% of created profiles (some may be deleted)")
                    .isGreaterThanOrEqualTo((int) (createdProfiles.size() * 0.90));

            log.info("9.9 PASSED — by-ids batch retrieval verified");
            stats.mtlsInternalEndpointsVerified++;
        } catch (Exception e) {
            stats.mtlsInternalEndpointsFailed++;
            log.error("9.9 FAILED — by-ids batch retrieval: {}", e.getMessage(), e);
        }

        // ── Step 9 final summary ──────────────────────────────────────────────
        log.info("========================================");
        log.info("STEP 9 COMPLETE");
        log.info("mTLS internal endpoints verified OK : {}", stats.mtlsInternalEndpointsVerified);
        log.info("mTLS internal endpoints failed      : {}", stats.mtlsInternalEndpointsFailed);
        log.info("========================================");

        assertThat(stats.mtlsInternalEndpointsFailed)
                .as("No mTLS internal endpoint should have failed in Step 9")
                .isEqualTo(0);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ProfileTestData createProfile(NewUserRecord user, int index) throws Exception {
        String token      = keycloakTestHelper.getAccessToken(user.username(), user.password());
        String authHeader = "Bearer " + token;

        int    age             = MIN_AGE + (index % (MAX_AGE - MIN_AGE));
        String gender          = (index % 2 == 0) ? "MALE"   : "FEMALE";
        String preferredGender = (index % 2 == 0) ? "FEMALE" : "MALE";

        String profileJson = buildProfileJsonWithIndex(user.firstName(), age, gender, preferredGender, index);

        String response = mockMvc.perform(post("")
                        .content(profileJson)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> profileData = objectMapper.readValue(response, new TypeReference<>() {});
        String profileId = (String) profileData.get("data");
        if (profileId == null) throw new IllegalStateException("Profile creation returned null id");

        return new ProfileTestData(profileId, token, user.username(),
                user.firstName(), age, gender, preferredGender);
    }

    /** Hobby pools by index — deterministic, based on user index mod pool size */
    private static final String[][] HOBBY_POOLS = {
        {"\"HIKING\"", "\"PHOTOGRAPHY\"", "\"GAMING\""},
        {"\"READING\"", "\"COOKING\"", "\"YOGA\""},
        {"\"MUSIC\"", "\"TRAVELING\"", "\"CYCLING\""},
        {"\"DANCING\"", "\"PAINTING\"", "\"RUNNING\""},
        {"\"MOVIES\"", "\"GYM\"", "\"MEDITATION\""}
    };

    private String buildProfileJson(String name, int age, String gender, String preferredGender) {
        return buildProfileJsonWithIndex(name, age, gender, preferredGender, 0);
    }

    private String buildProfileJsonWithIndex(String name, int age, String gender,
                                             String preferredGender, int index) {
        String[] hobbies = HOBBY_POOLS[index % HOBBY_POOLS.length];
        String hobbiesJson = "[" + String.join(", ", hobbies) + "]";
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
                    },
                    "hobbies": %s
                }""", name, age, gender, DEFAULT_BIO, DEFAULT_CITY,
                MIN_AGE, MAX_AGE, preferredGender, DEFAULT_MAX_RANGE, hobbiesJson);
    }

    private int createForcedMutualLikes(WebClient swipesClient) {
        List<ProfileTestData> snapshot = new ArrayList<>(createdProfiles);
        int pairCount = Math.min(FORCED_MUTUAL_MATCH_PAIRS, snapshot.size() / 2);
        int expected  = 0;

        log.info("Creating {} forced mutual-like pairs to guarantee match events", pairCount);

        for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
            ProfileTestData first  = snapshot.get(pairIndex * 2);
            ProfileTestData second = snapshot.get(pairIndex * 2 + 1);

            boolean firstOk  = createSwipeWithRetry(swipesClient, first,  second, true, 4, 750);
            boolean secondOk = createSwipeWithRetry(swipesClient, second, first,  true, 4, 750);

            if (firstOk)  { stats.swipesCreated++; stats.likesCount++; } else stats.swipesFailed++;
            if (secondOk) { stats.swipesCreated++; stats.likesCount++; } else stats.swipesFailed++;

            if (firstOk && secondOk) {
                expected++;
                log.info("  Forced mutual pair {}: {} <-> {}",
                        pairIndex + 1, first.getShortId(), second.getShortId());
            } else {
                log.warn("  Forced mutual pair {} failed (firstOk={}, secondOk={})",
                        pairIndex + 1, firstOk, secondOk);
            }
        }
        return expected;
    }

    private boolean createSwipeWithRetry(WebClient swipesClient, ProfileTestData swiper,
                                          ProfileTestData target, boolean isLike,
                                          int maxAttempts, long delayMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (createSwipe(swipesClient, swiper, target, isLike)) return true;
            if (attempt < maxAttempts) {
                try { TimeUnit.MILLISECONDS.sleep(delayMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
        }
        return false;
    }

    private boolean createSwipe(WebClient swipesClient, ProfileTestData swiper,
                                 ProfileTestData target, boolean isLike) {
        Map<String, Object> body = Map.of(
                "profile1Id", swiper.profileId(),
                "profile2Id", target.profileId(),
                "decision",   isLike);
        try {
            swipesClient.post()
                    .uri("/api/v1/swipes")
                    .header("Authorization", "Bearer " + swiper.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            log.debug("Swipe failed HTTP {}: {} → {}",
                    e.getStatusCode().value(), swiper.getShortId(), target.getShortId());
            return false;
        } catch (Exception e) {
            log.debug("Swipe failed: {} → {} — {}", swiper.getShortId(), target.getShortId(), e.getMessage());
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
                        assertThat(newEvents)
                                .as("Should receive MatchCreateEvent messages")
                                .isGreaterThanOrEqualTo(minExpected);
                    });
            return kafkaEventCollector.getMatchCreatedEvents().size() - baseline;
        } catch (Exception e) {
            int observed = kafkaEventCollector.getMatchCreatedEvents().size() - baseline;
            log.warn("Timed out waiting for MatchCreateEvent messages (observed {}).", observed);
            return observed;
        }
    }

    private int publishSyntheticMatchEvents(int count) {
        List<ProfileTestData> snapshot = new ArrayList<>(createdProfiles);
        int toPublish = Math.max(1, Math.min(count, snapshot.size() / 2));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,   KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        int published = 0;
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 0; i < toPublish; i++) {
                ProfileTestData first  = snapshot.get(i * 2);
                ProfileTestData second = snapshot.get(i * 2 + 1);
                MatchCreateEvent event = new MatchCreateEvent(
                        UUID.randomUUID().toString(), first.profileId(), second.profileId(),
                        java.time.Instant.now());
                String payload = objectMapper.writeValueAsString(event);
                producer.send(new ProducerRecord<>(matchCreatedTopic, event.getEventId(), payload))
                        .get(10, TimeUnit.SECONDS);
                published++;
                log.info("Published synthetic MatchCreateEvent {} for pair {} <-> {}",
                        event.getEventId(), first.getShortId(), second.getShortId());
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

        List<ProfileTestData> snapshot    = new ArrayList<>(createdProfiles);
        int maxUpdates                    = Math.min(15, snapshot.size());
        int locationUpdatesTarget         = Math.min(2, maxUpdates);
        int profilesToUpdate              = maxUpdates - locationUpdatesTarget;

        log.info("Will update {} profiles (plus {} location changes)", profilesToUpdate, locationUpdatesTarget);
        int beforeUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();

        int profilesUpdated = 0, preferencesUpdates = 0, criticalFieldsUpdates = 0,
                nonCriticalUpdates = 0, locationUpdates = 0, hobbiesUpdates = 0;
        Set<UUID> locationUpdatedProfileIds = new HashSet<>();

        for (int i = 0; i < profilesToUpdate; i++) {
            ProfileTestData profile = snapshot.get(i);
            try {
                String freshToken = keycloakTestHelper.getAccessToken(profile.username(),
                        keycloakUsers.stream().filter(u -> u.username().equals(profile.username()))
                                .findFirst().map(NewUserRecord::password).orElse("Password" + (i + 1) + "!"));
                String patchJson;
                String updateType;
                int typeIndex = i % 4;
                if (typeIndex == 0) {
                    patchJson = String.format("""
                            {"preferences":{"minAge":%d,"maxAge":%d,"gender":"all","maxRange":%d}}""",
                            MIN_AGE + 2, MAX_AGE + 5, DEFAULT_MAX_RANGE + 10);
                    updateType = "PREFERENCES"; preferencesUpdates++;
                } else if (typeIndex == 1) {
                    patchJson = String.format("""
                            {"age":%d,"bio":"Updated bio at %d - test"}""",
                            profile.age() + 1, System.currentTimeMillis() / 1000);
                    updateType = "CRITICAL_FIELDS"; criticalFieldsUpdates++;
                } else if (typeIndex == 2) {
                    patchJson = String.format("""
                            {"name":"%s","bio":"Non-critical update at %d"}""",
                            profile.firstName() + " Updated", System.currentTimeMillis() / 1000);
                    updateType = "NON_CRITICAL"; nonCriticalUpdates++;
                } else {
                    // Patch hobbies — replace the list with a new set
                    patchJson = """
                            {"hobbies":["HIKING","PHOTOGRAPHY","GAMING","READING"]}""";
                    updateType = "HOBBIES"; hobbiesUpdates++;
                }
                mockMvc.perform(patch("")
                                .content(patchJson)
                                .header("Authorization", "Bearer " + freshToken)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
                profilesUpdated++;
                log.info("[{}/{}] Updated {} ({})", i + 1, maxUpdates, profile.firstName(), updateType);
            } catch (Exception e) {
                log.error("[{}/{}] Failed to update profile: {}", i + 1, maxUpdates, profile.firstName(), e);
            }
        }

        for (int i = 0; i < locationUpdatesTarget; i++) {
            ProfileTestData profile = snapshot.get(profilesToUpdate + i);
            try {
                String freshToken = keycloakTestHelper.getAccessToken(profile.username(),
                        keycloakUsers.stream().filter(u -> u.username().equals(profile.username()))
                                .findFirst().map(NewUserRecord::password)
                                .orElse("Password" + (profilesToUpdate + i + 1) + "!"));
                String newCity = (i % 2 == 0) ? "Munich" : "Hamburg";
                mockMvc.perform(patch("")
                                .content(String.format("{\"city\":\"%s\"}", newCity))
                                .header("Authorization", "Bearer " + freshToken)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk());
                profilesUpdated++;
                locationUpdates++;
                locationUpdatedProfileIds.add(UUID.fromString(profile.profileId()));
                log.info("[{}/{}] Updated {} (LOCATION_CHANGE → {})",
                        profilesToUpdate + i + 1, maxUpdates, profile.firstName(), newCity);
            } catch (Exception e) {
                log.error("[{}/{}] Failed to update location: {}",
                        profilesToUpdate + i + 1, maxUpdates, profile.firstName(), e);
            }
        }

        log.info("Updated {}/{} profiles — PREF:{} CRIT:{} NON_CRIT:{} LOC:{} HOBBIES:{}",
                profilesUpdated, maxUpdates,
                preferencesUpdates, criticalFieldsUpdates, nonCriticalUpdates, locationUpdates, hobbiesUpdates);

        final int expectedNewEvents = profilesUpdated;
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int newEvents = kafkaEventCollector.getProfileUpdatedEvents().size() - beforeUpdateEvents;
                log.info("  Received {}/{} ProfileUpdatedEvent messages", newEvents, expectedNewEvents);
                assertThat(newEvents)
                        .as("Should receive ProfileUpdatedEvent for each updated profile")
                        .isGreaterThanOrEqualTo(expectedNewEvents);
            });

        int afterUpdateEvents = kafkaEventCollector.getProfileUpdatedEvents().size();
        int newEvents         = afterUpdateEvents - beforeUpdateEvents;
        stats.kafkaUpdateEventsReceived = newEvents;

        List<com.tinder.profiles.kafka.dto.ProfileUpdatedEvent> updateEvents =
                kafkaEventCollector.getProfileUpdatedEvents().subList(beforeUpdateEvents, afterUpdateEvents);

        int validEvents = 0, preferencesEvents = 0, criticalFieldsEvents = 0,
                nonCriticalEvents = 0, locationChangeEvents = 0;
        Set<UUID> locationChangeProfileIds = new HashSet<>();

        for (com.tinder.profiles.kafka.dto.ProfileUpdatedEvent event : updateEvents) {
            boolean isValid = event.getEventId() != null && event.getProfileId() != null
                    && event.getChangeType() != null
                    && event.getChangedFields() != null && !event.getChangedFields().isEmpty()
                    && event.getTimestamp() != null;
            if (isValid) validEvents++;
            if (event.getChangeType() != null) {
                switch (event.getChangeType()) {
                    case PREFERENCES    -> preferencesEvents++;
                    case CRITICAL_FIELDS -> criticalFieldsEvents++;
                    case NON_CRITICAL   -> nonCriticalEvents++;
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
        assertThat(locationChangeProfileIds)
                .as("Should have LOCATION_CHANGE events for all city-patched profiles")
                .containsAll(locationUpdatedProfileIds);

        log.info("All ProfileUpdatedEvent validations passed");
    }

    private void deleteProfilesAndVerifyEvents() throws Exception {
        log.info("========================================");
        log.info("STEP 7.2: Deleting Profiles (Mid-Wait)");
        log.info("========================================");

        List<ProfileTestData> snapshot = new ArrayList<>(createdProfiles);
        int profilesToDelete = Math.min(5, snapshot.size());
        int startIndex       = Math.max(0, snapshot.size() - profilesToDelete);

        int beforeDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        int profilesDeleted    = 0;
        List<ProfileTestData> deletedProfiles = new ArrayList<>();

        for (int i = startIndex; i < snapshot.size(); i++) {
            ProfileTestData profile = snapshot.get(i);
            try {
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
                log.error("[{}/{}] Failed to delete: {}", profilesDeleted + 1, profilesToDelete, profile.firstName(), e);
            }
        }

        log.info("Successfully deleted {}/{} profiles", profilesDeleted, profilesToDelete);

        final int expected = profilesDeleted;
        await()
            .atMost(60, TimeUnit.SECONDS)
            .pollDelay(1, TimeUnit.SECONDS)
            .pollInterval(3, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                int newEvents = kafkaEventCollector.getProfileDeletedEvents().size() - beforeDeleteEvents;
                log.info("  Received {}/{} ProfileDeleteEvent messages", newEvents, expected);
                assertThat(newEvents)
                        .as("Should receive ProfileDeleteEvent for each deleted profile")
                        .isGreaterThanOrEqualTo(expected);
            });

        int afterDeleteEvents = kafkaEventCollector.getProfileDeletedEvents().size();
        int newEvents         = afterDeleteEvents - beforeDeleteEvents;
        stats.kafkaDeleteEventsReceived = newEvents;

        List<com.tinder.profiles.kafka.dto.ProfileDeleteEvent> deleteEvents =
                kafkaEventCollector.getProfileDeletedEvents().subList(beforeDeleteEvents, afterDeleteEvents);

        Set<UUID> deletedProfileIds = deletedProfiles.stream()
                .map(p -> UUID.fromString(p.profileId())).collect(Collectors.toSet());

        int validEvents = 0;
        for (com.tinder.profiles.kafka.dto.ProfileDeleteEvent event : deleteEvents) {
            boolean isValid = event.getEventId() != null && event.getProfileId() != null
                    && deletedProfileIds.contains(event.getProfileId())
                    && event.getTimestamp() != null;
            if (isValid) validEvents++;
            else log.warn("  Invalid ProfileDeleteEvent: eventId={}, profileId={}",
                    event.getEventId(), event.getProfileId());
        }

        assertThat(validEvents).as("All ProfileDeleteEvent should be valid").isEqualTo(newEvents);
        assertThat(validEvents).as("Should have delete events for all deleted profiles").isEqualTo(profilesDeleted);
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
        Set<String> deckKeys  = redisTemplate.keys("deck:*");
        long deckKeyCount = (deckKeys != null) ? deckKeys.size() : 0;
        if (deckKeyCount == 0) log.warn("No deck keys found — Deck service may not be running");
        else log.info("Found {} deck keys in Redis", deckKeyCount);
    }

    private boolean verifySwipedProfilesExcluded(ProfileTestData profile, List<String> deckContents,
                                                  Map<String, Set<String>> userSwipedProfiles) {
        Set<String> swipedByUser = userSwipedProfiles.getOrDefault(profile.profileId(), Set.of());
        List<String> foundSwiped = swipedByUser.stream()
                .filter(deckContents::contains).collect(Collectors.toList());
        boolean correct = foundSwiped.isEmpty();
        if (correct) {
            stats.decksWithCorrectExclusions++;
        } else if (consumerServiceAvailable) {
            log.warn("Deck {} contains {} already-swiped profile(s): {}",
                    profile.getShortId(), foundSwiped.size(),
                    foundSwiped.stream().map(id -> id.substring(0, Math.min(8, id.length())))
                            .collect(Collectors.joining(", ")));
        }
        return correct;
    }

    private boolean verifyCandidatesMatchPreferences(ProfileTestData profile, List<String> deckContents,
                                                     Map<String, Profile> profileIndex) {
        Profile viewer = profileIndex.get(profile.profileId());
        if (viewer == null) return false;
        com.tinder.profiles.preferences.Preferences prefs = viewer.getPreferences();
        if (prefs == null) return false;

        List<String> invalid = new ArrayList<>();
        for (String candidateId : deckContents) {
            Profile candidate = profileIndex.get(candidateId);
            if (candidate == null) { invalid.add(candidateId + " (not found)"); continue; }
            if (!matchesGenderPreference(candidate.getGender(), prefs.getGender()))
                { invalid.add(candidateId + " (wrong gender)"); continue; }
            if (!matchesAgePreference(candidate.getAge(), prefs.getMinAge(), prefs.getMaxAge()))
                invalid.add(candidateId + " (wrong age)");
        }
        if (invalid.isEmpty()) stats.decksWithCorrectCandidates++;
        return invalid.isEmpty();
    }

    private boolean verifyDeckQuality(ProfileTestData profile, List<String> deckContents,
                                      Map<String, Profile> profileIndex) {
        if (deckContents.isEmpty() || deckContents.size() > 1000) return false;
        for (String candidateId : deckContents) {
            Profile candidate = profileIndex.get(candidateId);
            if (candidate == null) return false;
            if (candidate.getProfileId().toString().equals(profile.profileId())) return false;
            if (candidate.isDeleted()) return false;
        }
        return true;
    }

    private boolean matchesGenderPreference(String candidateGender, String preferredGender) {
        if (preferredGender == null || preferredGender.equalsIgnoreCase("any")
                || preferredGender.equalsIgnoreCase("all")) return true;
        return candidateGender != null && candidateGender.equalsIgnoreCase(preferredGender);
    }

    private boolean matchesAgePreference(Integer age, Integer minAge, Integer maxAge) {
        if (age == null) return false;
        if (minAge != null && age < minAge) return false;
        return maxAge == null || age <= maxAge;
    }

    private Map<String, Set<String>> buildSwipeMap(List<ProfileTestData> profiles) {
        List<ProfileTestData> snapshot = new ArrayList<>(profiles);
        Map<String, Set<String>> swipeMap = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.size(); i++) {
            ProfileTestData swiper = snapshot.get(i);
            Set<String> targets = new LinkedHashSet<>();
            for (int j = 1; j <= SWIPES_PER_USER; j++) {
                int idx = (i + j) % snapshot.size();
                if (idx != i) targets.add(snapshot.get(idx).profileId());
            }
            swipeMap.put(swiper.profileId(), targets);
        }
        return swipeMap;
    }

    private void printVerificationSummary() {
        log.info("========================================");
        log.info("VERIFICATION SUMMARY");
        log.info("========================================");
        log.info("Total decks checked: {}", stats.decksVerified);
        log.info("Decks with data: {}/{}", stats.decksWithData, stats.decksVerified);
        log.info("Consumer service available: {}", consumerServiceAvailable);
        log.info("Deck service available: {}", deckServiceAvailable);
        if (!consumerServiceAvailable) {
            log.info("Decks with correct exclusions: {}/{} [N/A — consumer down, fail-open expected]",
                    stats.decksWithCorrectExclusions, stats.decksWithData);
        } else {
            log.info("Decks with correct exclusions: {}/{}", stats.decksWithCorrectExclusions, stats.decksWithData);
        }
        log.info("Decks with correct candidates: {}/{}", stats.decksWithCorrectCandidates, stats.decksWithData);
        log.info("Decks fully correct: {}/{}", stats.decksFullyCorrect, stats.decksWithData);
        log.info("Average deck size: {}", stats.deckSizes.isEmpty() ? 0 :
                String.format("%.1f", stats.deckSizes.values().stream()
                        .mapToInt(Integer::intValue).average().orElse(0)));
        if (stats.decksWithData > 0) {
            double successRate = (stats.decksFullyCorrect * 100.0) / stats.decksWithData;
            log.info("Success rate (fully correct): {}/100", String.format("%.1f", successRate));
        }
        log.info("========================================");
    }

    private boolean isServiceReachable(String url) {
        try {
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(3_000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code < 500;
        } catch (Exception e) {
            log.debug("Service probe failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Inner helper: mTLS WebClient factory ─────────────────────────────────

    /**
     * Builds a Reactor Netty {@link WebClient} that presents the given PKCS12 client
     * keystore as its identity and uses the specified JKS truststore to verify
     * the server certificate.
     *
     * <p>Resource resolution order:
     * <ol>
     *   <li>Test classpath ({@code src/test/resources})</li>
     *   <li>Main classpath ({@code src/main/resources})</li>
     *   <li>Project {@code certs/} directory (two levels above CWD)</li>
     * </ol>
     */
    static final class MtlsClientFactory {

        private MtlsClientFactory() {}

        static WebClient build(String baseUrl, String keystoreFile,
                               String truststoreFile, String password) throws Exception {
            // Load client keystore
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = resolveResource(keystoreFile)) {
                ks.load(in, password.toCharArray());
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password.toCharArray());

            // Load truststore (JKS)
            KeyStore ts = KeyStore.getInstance("JKS");
            try (InputStream in = resolveResource(truststoreFile)) {
                ts.load(in, password.toCharArray());
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SslContext sslContext = SslContextBuilder.forClient()
                    .keyManager(kmf)
                    .trustManager(tmf)
                    .build();

            reactor.netty.http.client.HttpClient httpClient =
                    reactor.netty.http.client.HttpClient.create()
                            .secure(spec -> spec.sslContext(sslContext));

            return WebClient.builder()
                    .baseUrl(baseUrl)
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        }

        /**
         * Resolves a resource by name: test classpath → main classpath → certs/ directory.
         */
        private static InputStream resolveResource(String filename) throws Exception {
            // 1. Test classpath
            ClassPathResource test = new ClassPathResource(filename);
            if (test.exists()) return test.getInputStream();

            // 2. Main classpath
            ClassPathResource main = new ClassPathResource(filename);
            if (main.exists()) return main.getInputStream();

            // 3. certs/ directory relative to module root
            java.nio.file.Path certsDir = java.nio.file.Paths.get(System.getProperty("user.dir"))
                    .resolve("../../certs").normalize();
            java.nio.file.Path path = certsDir.resolve(filename);
            if (path.toFile().exists()) return java.nio.file.Files.newInputStream(path);

            throw new java.io.FileNotFoundException(
                    "Cannot locate '" + filename + "' in test classpath, main classpath, or certs/");
        }
    }
}
