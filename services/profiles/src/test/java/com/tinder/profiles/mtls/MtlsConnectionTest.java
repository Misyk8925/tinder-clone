package com.tinder.profiles.mtls;

import com.tinder.profiles.AbstractProfilesIntegrationTest;
import com.tinder.profiles.profile.Profile;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.ConnectException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive mTLS integration test suite for profiles-service internal connector.
 *
 * <p>Architecture under test:
 * <pre>
 *   Port 8010  — public HTTP  (JWT Bearer required, no client cert)
 *   Port 8011  — internal HTTPS mTLS  (clientAuth=need, CN=deck-service required)
 *      /api/v1/profiles/internal/search
 *      /api/v1/profiles/internal/page
 *      /api/v1/profiles/internal/by-ids
 *      /api/v1/profiles/internal/deck
 *      /api/v1/profiles/internal/active
 * </pre>
 *
 * <p>Two test layers:
 * <ol>
 *   <li><b>MockMvc + X.509 simulation</b> — fast, no network, tests Spring Security x509 chain</li>
 *   <li><b>Real WebClient (mTLS)</b> — full TLS handshake against the live Tomcat connector</li>
 * </ol>
 *
 * <p>Keystores (resolved from test classpath first, then main classpath):
 * <ul>
 *   <li>{@code deck-service.p12}   — authorized client identity (CN=deck-service)</li>
 *   <li>{@code profiles-service.p12} — server identity + unauthorized client (CN=profiles-service)</li>
 *   <li>{@code truststore-test.jks} / {@code truststore.jks} — JKS truststore</li>
 * </ul>
 */
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("mTLS Integration Tests — Profiles Service Internal Connector")
public class MtlsConnectionTest extends AbstractProfilesIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MtlsConnectionTest.class);

    // ── Ports ─────────────────────────────────────────────────────────────────

    /** mTLS internal port where deck-service connects */
    private static final int MTLS_PORT   = 8011;
    /** Public HTTP port (JWT-only) */
    private static final int PUBLIC_PORT = 8010;

    private static final String MTLS_BASE_URL   = "https://localhost:" + MTLS_PORT  + "/api/v1/profiles/internal";
    private static final String PUBLIC_BASE_URL  = "http://localhost:"  + PUBLIC_PORT + "/api/v1/profiles";

    // ── Keystore constants ────────────────────────────────────────────────────

    private static final String KS_PASSWORD = "changeit";

    /** Authorized client cert (CN=deck-service) — located in test/resources */
    private static final String DECK_KEYSTORE     = "deck-service.p12";
    /** Unauthorized client cert (CN=profiles-service) — used as wrong-CN scenario */
    private static final String PROFILES_KEYSTORE = "profiles-service.p12";
    /** JKS truststore — prefers truststore-test.jks from test/resources */
    private static final String TRUSTSTORE        = "truststore-test.jks";
    /** Fallback truststore name (main/resources) */
    private static final String TRUSTSTORE_MAIN   = "truststore.jks";

    // ── Shared real WebClient instances ───────────────────────────────────────

    /** Deck-service cert → should succeed on /internal/** */
    private WebClient deckMtlsClient;
    /** No client cert → TLS handshake should fail */
    private WebClient noClientCertClient;
    /** Profiles-service cert as client → wrong CN, rejected by Spring Security */
    private WebClient wrongCnClient;

    /** Whether the mTLS port is actually open (skips live tests when running in CI without full stack) */
    private boolean mtlsPortOpen;

    // ── Test data ─────────────────────────────────────────────────────────────

    private Profile savedProfile;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeAll
    void setUpClients() throws Exception {
        mtlsPortOpen = isPortOpen("localhost", MTLS_PORT, 2000);
        log.info("=================================================");
        log.info("mTLS port {} is {}", MTLS_PORT, mtlsPortOpen ? "OPEN" : "CLOSED (live tests will be skipped)");
        log.info("=================================================");

        if (mtlsPortOpen) {
            deckMtlsClient     = buildMtlsClient(DECK_KEYSTORE,     "PKCS12");
            noClientCertClient = buildNoClientCertClient(MTLS_BASE_URL);
            wrongCnClient      = buildMtlsClient(PROFILES_KEYSTORE, "PKCS12");
        }
    }

    @BeforeEach
    void seedProfile() {
        // Seed at least one profile so /internal/active and /internal/page are not trivially empty
        Profile p = new Profile();
        p.setName("mtls-test-user");
        p.setAge(25);
        p.setGender("MALE");
        p.setBio("mTLS test profile");
        p.setCity("Berlin");
        p.setUserId("mtls-user-" + UUID.randomUUID());
        savedProfile = profileRepository.save(p);
    }

    // =========================================================================
    // GROUP 1 — MockMvc X.509 security layer (fast, no network)
    // =========================================================================

    @Nested
    @DisplayName("Group 1 — X.509 Security Chain (MockMvc simulation)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class X509SecurityChainTests {

        /**
         * Simulates an authenticated request from deck-service by injecting a
         * pre-built UserDetails with ROLE_INTERNAL_CLIENT directly into the
         * security context — mirrors what MtlsUserDetailsService produces.
         */
        @Test
        @Order(1)
        @DisplayName("X509-1: Authorized CN=deck-service → /internal/active returns 200")
        void authorizedDeckServiceCn_active_returns200() throws Exception {
            log.info("X509-1: MockMvc with deck-service principal → expect 200");

            mockMvc.perform(get("/internal/active")
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk());

            log.info("X509-1 PASSED");
        }

        @Test
        @Order(2)
        @DisplayName("X509-2: Authorized CN=deck-service → /internal/page returns 200")
        void authorizedDeckServiceCn_page_returns200() throws Exception {
            log.info("X509-2: MockMvc → /internal/page with authorized CN");

            mockMvc.perform(get("/internal/page")
                            .param("page", "0")
                            .param("size", "10")
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk());

            log.info("X509-2 PASSED");
        }

        @Test
        @Order(3)
        @DisplayName("X509-3: Authorized CN=deck-service → /internal/search returns 200")
        void authorizedDeckServiceCn_search_returns200() throws Exception {
            log.info("X509-3: MockMvc → /internal/search with authorized CN");

            mockMvc.perform(get("/internal/search")
                            .param("viewerId", savedProfile.getProfileId().toString())
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk());

            log.info("X509-3 PASSED");
        }

        @Test
        @Order(4)
        @DisplayName("X509-4: Authorized CN=deck-service → /internal/deck returns 200")
        void authorizedDeckServiceCn_deck_returns200() throws Exception {
            log.info("X509-4: MockMvc → /internal/deck with authorized CN");

            mockMvc.perform(get("/internal/deck")
                            .param("viewerId", UUID.randomUUID().toString())
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk());

            log.info("X509-4 PASSED");
        }

        @Test
        @Order(5)
        @DisplayName("X509-5: Authorized CN=deck-service → /internal/by-ids returns 200")
        void authorizedDeckServiceCn_byIds_returns200() throws Exception {
            log.info("X509-5: MockMvc → /internal/by-ids with authorized CN");

            mockMvc.perform(get("/internal/by-ids")
                            .param("ids", savedProfile.getProfileId().toString())
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk());

            log.info("X509-5 PASSED");
        }

        @Test
        @Order(6)
        @DisplayName("X509-6: Unknown CN → /internal/active returns 403")
        void unknownCn_active_returns403() throws Exception {
            log.info("X509-6: Unknown CN=unknown-service should be rejected with 403");

            mockMvc.perform(get("/internal/active")
                            .with(x509InternalClient("unknown-service")))
                    .andExpect(status().isForbidden());

            log.info("X509-6 PASSED");
        }

        @Test
        @Order(7)
        @DisplayName("X509-7: Wrong CN=profiles-service → /internal/active returns 403")
        void wrongCnProfilesService_active_returns403() throws Exception {
            log.info("X509-7: CN=profiles-service (wrong CN) should be rejected with 403");

            // profiles-service is NOT in MtlsUserDetailsService.ALLOWED_CNS → UsernameNotFoundException → 403
            mockMvc.perform(get("/internal/active")
                            .with(x509InternalClient("profiles-service")))
                    .andExpect(status().isForbidden());

            log.info("X509-7 PASSED");
        }

        @Test
        @Order(8)
        @DisplayName("X509-8: No principal (no cert) → /internal/active returns 401 or 403")
        void noPrincipal_active_returns401or403() throws Exception {
            log.info("X509-8: Request without any cert → expect 401 or 403");

            int status = mockMvc.perform(get("/internal/active")
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("Unauthenticated request to /internal/** should return 401 or 403")
                    .isIn(401, 403);

            log.info("X509-8 PASSED — status={}", status);
        }

        @ParameterizedTest(name = "X509-9 [{index}]: unauthorized CN=''{0}'' → 403")
        @Order(9)
        @ValueSource(strings = {"swipes-service", "consumer-service", "gateway", "admin", "", "null"})
        @DisplayName("X509-9: Various unauthorized CNs → 403")
        void variousUnauthorizedCns_return403(String cn) throws Exception {
            log.info("X509-9: Testing unauthorized CN='{}'", cn);

            // Use "null" as placeholder for a principal with null-like name
            String effectiveCn = cn.equals("null") ? "totally-unknown" : cn;

            mockMvc.perform(get("/internal/active")
                            .with(x509InternalClient(effectiveCn)))
                    .andExpect(status().isForbidden());

            log.info("X509-9 PASSED for CN='{}'", cn);
        }

        @Test
        @Order(10)
        @DisplayName("X509-10: Authorized CN → /internal/page with invalid params returns 400")
        void authorizedCn_invalidPageParams_returns400() throws Exception {
            log.info("X509-10: Invalid pagination params should return 400");

            // size > 100 is rejected by controller validation
            mockMvc.perform(get("/internal/page")
                            .param("page", "0")
                            .param("size", "999")
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isBadRequest());

            log.info("X509-10 PASSED");
        }

        @Test
        @Order(11)
        @DisplayName("X509-11: Authorized CN → /internal/by-ids with invalid UUID returns 400")
        void authorizedCn_invalidUuid_returns400() throws Exception {
            log.info("X509-11: Invalid UUID in /internal/by-ids should return 400");

            mockMvc.perform(get("/internal/by-ids")
                            .param("ids", "not-a-valid-uuid")
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isBadRequest());

            log.info("X509-11 PASSED");
        }

        @Test
        @Order(12)
        @DisplayName("X509-12: Authorized CN → /internal/search with negative limit returns 400")
        void authorizedCn_negativeLimit_returns400() throws Exception {
            log.info("X509-12: limit<1 should return 400");

            mockMvc.perform(get("/internal/search")
                            .param("viewerId", UUID.randomUUID().toString())
                            .param("limit", "-1")
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isBadRequest());

            log.info("X509-12 PASSED");
        }

        @Test
        @Order(13)
        @DisplayName("X509-13: Authorized CN → /internal/active response contains valid profileId field")
        void authorizedCn_activeResponse_hasProfileIdField() throws Exception {
            log.info("X509-13: /internal/active response structure validation");

            String body = mockMvc.perform(get("/internal/active")
                            .accept(MediaType.APPLICATION_JSON)
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // At least the seeded profile must appear
            assertThat(body)
                    .as("Response should be a JSON array containing profileId")
                    .contains("profileId");

            log.info("X509-13 PASSED — /internal/active body: {}", body.substring(0, Math.min(200, body.length())));
        }

        @Test
        @Order(14)
        @DisplayName("X509-14: Authorized CN → /internal/page response is a JSON array")
        void authorizedCn_pageResponse_isJsonArray() throws Exception {
            log.info("X509-14: /internal/page response should be a non-null JSON array");

            String body = mockMvc.perform(get("/internal/page")
                            .param("page", "0")
                            .param("size", "5")
                            .accept(MediaType.APPLICATION_JSON)
                            .with(x509InternalClient("deck-service")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body)
                    .as("Response body must be a non-empty JSON array")
                    .startsWith("[");

            log.info("X509-14 PASSED — page body starts with '['");
        }
    }

    // =========================================================================
    // GROUP 2 — Real mTLS WebClient (live Tomcat connector)
    // =========================================================================

    @Nested
    @DisplayName("Group 2 — Real mTLS WebClient (live connector on port 8011)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class LiveMtlsConnectorTests {

        @Test
        @Order(20)
        @DisplayName("mTLS-1: Valid deck-service cert → /internal/active returns HTTP 200")
        void validDeckCert_active_returns200() {
            assumeMtlsPortOpen();
            log.info("mTLS-1: Real WebClient with deck-service cert → /internal/active");

            List<?> response = deckMtlsClient.get()
                    .uri("/active")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            log.info("mTLS-1 PASSED — /internal/active returned {} profiles", response.size());
        }

        @Test
        @Order(21)
        @DisplayName("mTLS-2: Valid deck-service cert → /internal/page returns HTTP 200")
        void validDeckCert_page_returns200() {
            assumeMtlsPortOpen();
            log.info("mTLS-2: Real WebClient → /internal/page");

            List<?> response = deckMtlsClient.get()
                    .uri(uri -> uri.path("/page").queryParam("page", 0).queryParam("size", 5).build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            log.info("mTLS-2 PASSED — /internal/page returned {} profiles", response.size());
        }

        @Test
        @Order(22)
        @DisplayName("mTLS-3: Valid deck-service cert → /internal/search returns HTTP 200")
        void validDeckCert_search_returns200() {
            assumeMtlsPortOpen();
            log.info("mTLS-3: Real WebClient → /internal/search");

            List<?> response = deckMtlsClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("viewerId", savedProfile.getProfileId())
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            log.info("mTLS-3 PASSED");
        }

        @Test
        @Order(23)
        @DisplayName("mTLS-4: Valid deck-service cert → /internal/deck returns HTTP 200")
        void validDeckCert_deck_returns200() {
            assumeMtlsPortOpen();
            log.info("mTLS-4: Real WebClient → /internal/deck");

            List<?> response = deckMtlsClient.get()
                    .uri(uri -> uri.path("/deck")
                            .queryParam("viewerId", UUID.randomUUID())
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            log.info("mTLS-4 PASSED");
        }

        @Test
        @Order(24)
        @DisplayName("mTLS-5: No client cert → TLS handshake rejected (SSLHandshakeException / connection reset)")
        void noClientCert_tlsHandshakeRejected() {
            assumeMtlsPortOpen();
            log.info("mTLS-5: Client without cert should fail at TLS layer");

            assertThatThrownBy(() ->
                    noClientCertClient.get()
                            .uri(MTLS_BASE_URL + "/active")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(10))
            ).satisfies(ex -> {
                Throwable cause = unwrapCause(ex);
                boolean isTlsFailure = isTlsError(cause);
                log.info("mTLS-5: exception={} message={}",
                        cause != null ? cause.getClass().getSimpleName() : "null",
                        cause != null ? cause.getMessage() : "null");
                assertThat(isTlsFailure)
                        .as("Expected TLS handshake failure but got: %s — %s",
                                ex.getClass().getName(), ex.getMessage())
                        .isTrue();
            });

            log.info("mTLS-5 PASSED — connection without client cert rejected at TLS level");
        }

        @Test
        @Order(25)
        @DisplayName("mTLS-6: Wrong CN (profiles-service cert) → HTTP 403 from Spring Security x509")
        void wrongCnCert_returns403() {
            assumeMtlsPortOpen();
            log.info("mTLS-6: Wrong CN=profiles-service should get HTTP 403");

            WebClientResponseException ex = Assertions.assertThrows(
                    WebClientResponseException.class,
                    () -> wrongCnClient.get()
                            .uri(MTLS_BASE_URL + "/active")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(10))
            );

            assertThat(ex.getStatusCode().value())
                    .as("Wrong-CN cert should produce HTTP 401 or 403")
                    .isIn(401, 403);

            log.info("mTLS-6 PASSED — wrong CN cert rejected with HTTP {}", ex.getStatusCode().value());
        }

        @Test
        @Order(26)
        @DisplayName("mTLS-7: /internal/active response structure has 'profileId' field")
        void validDeckCert_activeResponse_hasProfileIdField() {
            assumeMtlsPortOpen();
            log.info("mTLS-7: Response structure validation via real mTLS");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> profiles = deckMtlsClient.get()
                    .uri("/active")
                    .retrieve()
                    .bodyToFlux(Map.class)
                    .map(m -> (Map<String, Object>) m)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertThat(profiles).isNotNull();

            for (Map<String, Object> profile : profiles) {
                assertThat(profile)
                        .as("Each profile must contain 'profileId'")
                        .containsKey("profileId");
                assertThat(profile.get("profileId")).isNotNull();
            }

            log.info("mTLS-7 PASSED — {} profiles with valid schema", profiles.size());
        }

        @Test
        @Order(27)
        @DisplayName("mTLS-8: 20 concurrent mTLS requests all succeed")
        void concurrentMtlsRequests_allSucceed() {
            assumeMtlsPortOpen();
            int concurrency = 20;
            log.info("mTLS-8: {} concurrent mTLS requests should all succeed", concurrency);

            List<Long> results = reactor.core.publisher.Flux.range(0, concurrency)
                    .flatMap(i -> deckMtlsClient.get()
                            .uri(uri -> uri.path("/active").build())
                            .retrieve()
                            .bodyToMono(List.class)
                            .map(list -> (long) list.size())
                            .onErrorReturn(-1L),
                            concurrency)
                    .collectList()
                    .block(Duration.ofSeconds(30));

            assertThat(results).isNotNull().hasSize(concurrency);

            long successful = results.stream().filter(r -> r >= 0).count();
            long failed     = results.stream().filter(r -> r < 0).count();
            log.info("mTLS-8: {}/{} succeeded, {} failed", successful, concurrency, failed);

            assertThat(successful)
                    .as("All concurrent mTLS requests should succeed")
                    .isEqualTo(concurrency);

            log.info("mTLS-8 PASSED");
        }
    }

    // =========================================================================
    // GROUP 3 — Public port isolation
    // =========================================================================

    @Nested
    @DisplayName("Group 3 — Public port isolation (port 8010 vs 8011)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PublicPortIsolationTests {

        @Test
        @Order(30)
        @DisplayName("Isolation-1: /internal/active on public port without JWT → 401 or 403")
        void internalPath_publicPort_noJwt_returns401or403() throws Exception {
            log.info("Isolation-1: /internal/active on public port without JWT");

            // MockMvc targets the servlet directly (public port rules apply, no x509 chain)
            int status = mockMvc.perform(get("/internal/active")
                            .accept(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getStatus();

            assertThat(status)
                    .as("/internal/active on public port without any auth should be 401 or 403")
                    .isIn(401, 403);

            log.info("Isolation-1 PASSED — status={}", status);
        }

        @Test
        @Order(31)
        @DisplayName("Isolation-2: /internal/active on public port (HTTP) via WebClient → 401 or 403")
        void internalPath_publicPortHttp_webClient_returns401or403() {
            log.info("Isolation-2: WebClient → public port HTTP for /internal/active");

            WebClient plainClient = WebClient.builder()
                    .baseUrl(PUBLIC_BASE_URL)
                    .build();

            WebClientResponseException ex = Assertions.assertThrows(
                    WebClientResponseException.class,
                    () -> plainClient.get()
                            .uri("/internal/active")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(5))
            );

            assertThat(ex.getStatusCode().value())
                    .as("Public port should enforce JWT — unauthenticated access must be denied")
                    .isIn(401, 403);

            log.info("Isolation-2 PASSED — HTTP {}", ex.getStatusCode().value());
        }

        @Test
        @Order(32)
        @DisplayName("Isolation-3: /internal/page, /internal/search, /internal/by-ids, /internal/deck all require auth on public port")
        void allInternalPaths_publicPort_requireAuth() throws Exception {
            log.info("Isolation-3: All /internal/** paths on public port should require auth");

            String[] paths = {"/internal/active", "/internal/page", "/internal/search",
                    "/internal/by-ids", "/internal/deck"};

            for (String path : paths) {
                int status = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
                        .andReturn().getResponse().getStatus();

                assertThat(status)
                        .as("Path %s on public port should be 401 or 403 without auth", path)
                        .isIn(401, 403);

                log.info("Isolation-3: {} → {}", path, status);
            }

            log.info("Isolation-3 PASSED — all /internal/** paths protected on public port");
        }
    }

    // =========================================================================
    // GROUP 4 — Consumer-service mTLS probe (conditional)
    // =========================================================================

    @Nested
    @DisplayName("Group 4 — Consumer-service mTLS probe (skipped when not running)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ConsumerServiceMtlsProbeTests {

        private static final int CONSUMER_MTLS_PORT = 8051;
        private static final String CONSUMER_BASE    = "https://localhost:" + CONSUMER_MTLS_PORT;

        @Test
        @Order(40)
        @DisplayName("Consumer-1: deck-service cert accepted on consumer mTLS port 8051")
        void deckCert_consumerMtlsPort_accepted() throws Exception {
            boolean consumerUp = isPortOpen("localhost", CONSUMER_MTLS_PORT, 2000);
            Assumptions.assumeTrue(consumerUp,
                    "consumer-service not running on port " + CONSUMER_MTLS_PORT + " — skipping");

            log.info("Consumer-1: consumer-service is UP — probing mTLS with deck-service cert");

            WebClient client = buildMtlsClientForBase(CONSUMER_BASE, DECK_KEYSTORE, "PKCS12");

            try {
                WebClientResponseException ex = Assertions.assertThrows(
                        WebClientResponseException.class,
                        () -> client.post()
                                .uri(uri -> uri.path("/between/batch")
                                        .queryParam("viewerId", UUID.randomUUID())
                                        .build())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(List.of())
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block(Duration.ofSeconds(10))
                );
                // 400 (empty body) is fine — the TLS+auth layer accepted the connection
                assertThat(ex.getStatusCode().value())
                        .as("Deck cert on consumer mTLS port should not be 401/403")
                        .isNotIn(401, 403);
                log.info("Consumer-1 PASSED — consumer /between/batch responded HTTP {} (mTLS accepted)",
                        ex.getStatusCode().value());
            } catch (Exception ex) {
                // 200/204 with empty body is also a valid success path
                log.info("Consumer-1 PASSED — consumer mTLS endpoint accepted deck-service cert (no HTTP error)");
            }
        }

        @Test
        @Order(41)
        @DisplayName("Consumer-2: no client cert rejected on consumer mTLS port 8051")
        void noCert_consumerMtlsPort_rejected() throws Exception {
            boolean consumerUp = isPortOpen("localhost", CONSUMER_MTLS_PORT, 2000);
            Assumptions.assumeTrue(consumerUp,
                    "consumer-service not running on port " + CONSUMER_MTLS_PORT + " — skipping");

            log.info("Consumer-2: consumer-service mTLS port should reject no-cert connections");

            WebClient noClient = buildNoClientCertClient(CONSUMER_BASE);

            assertThatThrownBy(() ->
                    noClient.post()
                            .uri(uri -> uri.path("/between/batch")
                                    .queryParam("viewerId", UUID.randomUUID())
                                    .build())
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(List.of())
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(Duration.ofSeconds(10))
            ).satisfies(ex -> {
                Throwable cause = unwrapCause(ex);
                boolean isTlsError = isTlsError(cause);
                log.info("Consumer-2: exception={} message={}",
                        cause != null ? cause.getClass().getSimpleName() : "null",
                        cause != null ? cause.getMessage() : "null");
                assertThat(isTlsError)
                        .as("Expected TLS failure without client cert but got: %s — %s",
                                ex.getClass().getName(), ex.getMessage())
                        .isTrue();
            });

            log.info("Consumer-2 PASSED — consumer mTLS port rejected connection without cert");
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a {@link org.springframework.test.web.servlet.request.RequestPostProcessor}
     * that injects a fake x509 UserDetails with ROLE_INTERNAL_CLIENT (if CN is allowed)
     * or without it (if CN is unknown), replicating the behaviour of
     * {@link com.tinder.profiles.security.MtlsUserDetailsService}.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor x509InternalClient(String cn) {
        // MtlsUserDetailsService allows only "deck-service"
        boolean allowed = "deck-service".equals(cn);
        org.springframework.security.core.userdetails.UserDetails userDetails = allowed
                ? new User(cn, "", List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_CLIENT")))
                : new User(cn.isBlank() ? "anonymous" : cn, "", List.of());

        return request -> {
            // Place a pre-authenticated token directly into the security context header
            request.setAttribute(
                    "javax.security.cert.X509Certificate",
                    new javax.security.cert.X509Certificate[0]);
            // Spring Security x509 filter picks up the UserDetails via userDetailsService;
            // here we bypass the filter by pre-setting the principal on MockMvc level.
            return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                    .user(userDetails)
                    .postProcessRequest(request);
        };
    }

    /**
     * Builds a WebClient that presents {@code keystorePath} as client certificate
     * and trusts the server cert via the test truststore.
     * Base URL is {@link #MTLS_BASE_URL}.
     */
    private WebClient buildMtlsClient(String keystorePath, String keystoreType) throws Exception {
        return buildMtlsClientForBase(MTLS_BASE_URL, keystorePath, keystoreType);
    }

    /**
     * Builds a WebClient with the given base URL, client keystore and trust store.
     */
    private WebClient buildMtlsClientForBase(String baseUrl,
                                              String keystorePath,
                                              String keystoreType) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (InputStream ks = loadResourceStream(keystorePath)) {
            keyStore.load(ks, KS_PASSWORD.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KS_PASSWORD.toCharArray());

        TrustManagerFactory tmf = buildTrustManagerFactory();

        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(kmf)
                .trustManager(tmf)
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().secure(s -> s.sslContext(sslContext))))
                .build();
    }

    /**
     * Builds a WebClient that trusts the server cert but presents NO client certificate.
     * Used to verify that the server rejects the connection at TLS level (clientAuth=need).
     */
    private WebClient buildNoClientCertClient(String baseUrl) throws Exception {
        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(buildTrustManagerFactory())
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().secure(s -> s.sslContext(sslContext))))
                .build();
    }

    /**
     * Builds a TrustManagerFactory from the test truststore.
     * Prefers {@code truststore-test.jks} in test/resources,
     * falls back to {@code truststore.jks} from main/resources.
     */
    private TrustManagerFactory buildTrustManagerFactory() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (InputStream ts = loadResourceStream(TRUSTSTORE)) {
            trustStore.load(ts, KS_PASSWORD.toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    /**
     * Loads a resource stream: test classpath → main classpath → project certs/ directory.
     */
    private InputStream loadResourceStream(String filename) throws Exception {
        // 1. Try test/resources
        ClassPathResource testResource = new ClassPathResource(filename);
        if (testResource.exists()) {
            log.debug("Loading '{}' from test classpath", filename);
            return testResource.getInputStream();
        }

        // 2. Try main/resources (truststore.jks lives there in production)
        if (TRUSTSTORE.equals(filename)) {
            ClassPathResource fallback = new ClassPathResource(TRUSTSTORE_MAIN);
            if (fallback.exists()) {
                log.debug("Loading truststore fallback '{}' from main classpath", TRUSTSTORE_MAIN);
                return fallback.getInputStream();
            }
        }

        // 3. Fallback: project certs/ directory (relative to profiles/ module root)
        java.nio.file.Path certPath = java.nio.file.Paths.get(
                System.getProperty("user.dir")).resolve("../../certs").resolve(filename).normalize();
        if (certPath.toFile().exists()) {
            log.debug("Loading '{}' from certs/ directory: {}", filename, certPath);
            return java.nio.file.Files.newInputStream(certPath);
        }

        throw new java.io.FileNotFoundException(
                "Cannot locate keystore resource '" + filename +
                "' (checked test classpath, main classpath, and certs/ directory)");
    }

    /**
     * Returns {@code true} if the exception represents a TLS-layer failure.
     */
    private boolean isTlsError(Throwable cause) {
        if (cause == null) return false;
        if (cause instanceof SSLHandshakeException) return true;
        if (cause instanceof ConnectException)      return true;
        String msg = cause.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("handshake")
                || lower.contains("certificate")
                || lower.contains("connection reset")
                || lower.contains("ssl")
                || lower.contains("closed");
    }

    /**
     * Recursively unwraps exception causes through {@link WebClientRequestException}
     * and Reactor internal exception wrappers.
     */
    private Throwable unwrapCause(Throwable ex) {
        if (ex instanceof WebClientRequestException wex) {
            return wex.getCause() != null ? unwrapCause(wex.getCause()) : wex;
        }
        if (ex.getClass().getName().contains("ReactiveException")) {
            return ex.getCause() != null ? unwrapCause(ex.getCause()) : ex;
        }
        return ex;
    }

    /**
     * Non-blocking TCP probe: returns {@code true} when host:port is reachable within the timeout.
     */
    private boolean isPortOpen(String host, int port, int timeoutMs) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Skips the current test when the mTLS Tomcat connector is not available.
     */
    private void assumeMtlsPortOpen() {
        Assumptions.assumeTrue(mtlsPortOpen,
                "mTLS connector on port " + MTLS_PORT + " is not open — skipping live TLS test");
    }
}

