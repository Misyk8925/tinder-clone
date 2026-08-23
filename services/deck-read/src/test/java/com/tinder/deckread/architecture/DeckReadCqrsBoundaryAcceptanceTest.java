package com.tinder.deckread.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-3 architecture acceptance for FR-2, FR-4, FR-5, FR-7 and FR-8.
 *
 * <p>These checks deliberately run without Kafka, Redis or Docker. They protect the service
 * ownership decision before the deeper materializer/cluster tests are implemented in phase 4.
 */
@Tag("acceptance")
@DisplayName("Feature: Deck Read owns an autonomous distributed read model")
class DeckReadCqrsBoundaryAcceptanceTest {

    private static final Path SERVICE = Path.of("").toAbsolutePath().normalize();
    private static final Path REPOSITORY = SERVICE.getParent().getParent();

    @Test
    @DisplayName("Scenario: Given the Deck Read source, when the read path is inspected, then Profiles and replica-local authoritative caches are absent")
    void readPathDoesNotDependOnSynchronousProfilesOrPerReplicaAuthoritativeCaches() throws IOException {
        // Given
        String main = javaSources(SERVICE.resolve("src/main/java"));
        String pom = Files.readString(SERVICE.resolve("pom.xml"));

        // When / Then
        assertThat(main)
                .doesNotContain("ProfilesClient")
                .doesNotContain("ProfileCache")
                .doesNotContain("ViewerIdentityCache")
                .contains("DeckCardProjectionBackfillClient");
        assertThat(pom).doesNotContain("caffeine");
    }

    @Test
    @DisplayName("Scenario: Given the existing Deck service, when Deck Read imports ordering, then ensure remains and source Redis is read-only")
    void deckEnsureRemainsAndSourceRedisIsReadOnly() throws IOException {
        // Given
        String main = javaSources(SERVICE.resolve("src/main/java"));

        // When / Then
        assertThat(SERVICE.resolve("src/main/java/com/tinder/deckread/client/DeckEnsureClient.java")).exists();
        assertThat(main).contains("DeckEnsureClient");
        assertThat(main).doesNotContain("deck:stale:", "deck:lock:", "deck:contains:", "deck:recent:viewers");
    }

    @Test
    @DisplayName("Scenario: Given two Redis responsibilities, when configuration is inspected, then source and read-model clients are distinct")
    void sourceAndReadModelUseDistinctNamedRedisClientsAndReadModelIsClustered() throws IOException {
        // Given
        String config = Files.readString(SERVICE.resolve("src/main/resources/application.properties"));

        // When / Then
        assertThat(config)
                .contains("quarkus.redis.deck-source.hosts")
                .contains("quarkus.redis.read-model.hosts")
                .contains("quarkus.redis.read-model.client-type=cluster")
                .contains("%prod.deck-read.auto-backfill-on-empty=${DECK_READ_AUTO_BACKFILL_ON_EMPTY:true}")
                .contains("%test.deck-read.auto-backfill-on-empty=false")
                .contains("quarkus.rest-client.profiles-backfill.url")
                .contains("%prod.quarkus.tls.profiles-backfill.hostname-verification-algorithm=NONE");
    }

    @Test
    @DisplayName("Scenario: Given the runtime Compose stack, when Deck Read Redis is configured, then the read model uses a dedicated standalone Redis")
    void runtimeComposeUsesDedicatedStandaloneRedisForTheReadModel() throws IOException {
        // Given
        String compose = Files.readString(REPOSITORY.resolve("docker-compose.yml"));
        String local = Files.readString(REPOSITORY.resolve("docker-compose.local.yml"));

        // When / Then
        assertThat(compose)
                .contains("deck-read-redis:")
                .contains("DECK_READ_REDIS_HOSTS: redis://deck-read-redis:6379")
                .contains("DECK_SOURCE_REDIS_HOST: redis")
                .contains("QUARKUS_REDIS_READ_MODEL_CLIENT_TYPE: standalone")
                .contains("DECK_READ_AUTO_BACKFILL_ON_EMPTY: \"true\"")
                .contains("PROFILES_INTERNAL_URL: https://profiles:8011")
                .doesNotContain("DECK_READ_REDIS_HOSTS: redis://redis:6379")
                .doesNotContain("deck-read-redis-1")
                .doesNotContain("DECK_READ_REDIS_HOSTS: >-");
        assertThat(local).contains("127.0.0.1:6380:6379");
    }

    @Test
    @DisplayName("Scenario: Given Docker IP churn in the isolated cluster fixture, when nodes are configured, then they advertise stable hostnames and data ports")
    void readModelClusterAdvertisesStableHostnamesAndDataPort() throws IOException {
        // Given
        String clusterCompose = Files.readString(REPOSITORY.resolve("docker-compose.deck-read-cluster.yml"));

        // When / Then
        assertThat(clusterCompose)
                .contains("cluster-preferred-endpoint-type hostname")
                .contains("cluster-announce-port 6379")
                .contains("cluster-announce-bus-port 16379")
                .contains("cluster-announce-hostname deck-read-redis-1")
                .contains("cluster-announce-hostname deck-read-redis-2")
                .contains("cluster-announce-hostname deck-read-redis-3");
    }

    @Test
    @DisplayName("Scenario: Given the runtime Compose stack, when service images are inspected, then location and swipes are Go, photos is Python, and unused ELK/Nexus/cluster services are absent")
    void runtimeComposeUsesGoAndPythonServicesAndOmitsUnusedInfra() throws IOException {
        // Given
        String compose = Files.readString(REPOSITORY.resolve("docker-compose.yml"));
        String local = Files.readString(REPOSITORY.resolve("docker-compose.local.yml"));

        // When / Then
        assertThat(compose)
                .contains("context: ./services/location-go")
                .contains("context: ./services/photos")
                .contains("context: ./services/swipes-go")
                .doesNotContain("context: ./services/swipes-demo")
                .doesNotContain("elasticsearch:")
                .doesNotContain("logstash:")
                .doesNotContain("kibana:")
                .doesNotContain("nexus:")
                .doesNotContain("elk:");
        assertThat(local).contains("SWIPES_JWT_ISSUER: http://localhost:9080/realms/spring");
        // Profiles has no healthcheck; service_healthy leaves swipes Created forever.
        assertThat(compose).contains("      profiles:\n        condition: service_started");
    }

    @Test
    @DisplayName("Scenario: Given the runtime Compose stack, when subscriptions starts, then every Stripe redirect uses one browser-reachable profile URL")
    void runtimeComposeKeepsStripeRedirectsPublicAndConsumerHealthReachable() throws IOException {
        String compose = Files.readString(REPOSITORY.resolve("docker-compose.yml"));
        String local = Files.readString(REPOSITORY.resolve("docker-compose.local.yml"));
        String consumerPom = Files.readString(REPOSITORY.resolve("services/consumer/pom.xml"));
        String consumerDockerfile = Files.readString(REPOSITORY.resolve("services/consumer/Dockerfile"));
        String subscriptionsProd = Files.readString(
                REPOSITORY.resolve("services/subscriptions/src/main/resources/application-prod.yaml"));

        String subscriptionsYaml = Files.readString(
                REPOSITORY.resolve("services/subscriptions/src/main/resources/application.yaml"));

        assertThat(compose)
                .contains("STRIPE_SUCCESS_URL: ${PUBLIC_APP_PROFILE_URL:-https://matchapp.misyk.tech/profile}")
                .contains("STRIPE_CANCEL_URL: ${PUBLIC_APP_PROFILE_URL:-https://matchapp.misyk.tech/profile}")
                .contains("STRIPE_RETURN_URL: ${PUBLIC_APP_PROFILE_URL:-https://matchapp.misyk.tech/profile}")
                .doesNotContain("STRIPE_SUCCESS_URL: ${STRIPE_SUCCESS_URL:")
                .doesNotContain("STRIPE_CANCEL_URL: ${STRIPE_CANCEL_URL:")
                .doesNotContain("STRIPE_RETURN_URL: ${STRIPE_RETURN_URL:")
                .doesNotContain("http://subscriptions:8095/success")
                .doesNotContain("http://subscriptions:8095/cancel")
                .contains("http://localhost:8050/actuator/health");
        assertThat(local)
                .contains("STRIPE_SUCCESS_URL: http://localhost:4200/profile")
                .contains("STRIPE_CANCEL_URL: http://localhost:4200/profile")
                .contains("STRIPE_RETURN_URL: http://localhost:4200/profile")
                .doesNotContain("localhost:8095")
                .doesNotContain("/subscriptions/return");
        assertThat(subscriptionsProd).contains("return-url: ${STRIPE_RETURN_URL:http://localhost:4200/profile}");
        assertThat(subscriptionsYaml)
                .contains("success-url: ${STRIPE_SUCCESS_URL:http://localhost:4200/profile}")
                .contains("cancel-url: ${STRIPE_CANCEL_URL:http://localhost:4200/profile}")
                .contains("return-url: ${STRIPE_RETURN_URL:http://localhost:4200/profile}")
                .doesNotContain("localhost:8095/success")
                .doesNotContain("localhost:8095/cancel")
                .doesNotContain("localhost:8095/return");
        assertThat(consumerPom).contains("spring-boot-starter-actuator");
        assertThat(consumerDockerfile).contains("apk add --no-cache wget");
    }

    @Test
    @DisplayName("Scenario: Given duplicate profile_cache user_id rows, when the identity index migration runs, then it keeps one row per user before creating the unique index")
    void swipesIdentityIndexMigrationDeduplicatesBeforeUniqueIndex() throws IOException {
        String sql = Files.readString(REPOSITORY.resolve("migrations/migration/V2_swipes_identity_index.sql"));

        assertThat(sql)
                .contains("DELETE FROM profile_cache AS stale")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS profile_cache_user_id_unique");
    }

    @Test
    @DisplayName("Scenario: Given a persisted cluster whose replicas advertise :0/noaddr, when init runs, then it refuses to treat cluster_state:ok as healthy")
    void clusterInitRejectsDisconnectedPortZeroReplicas() throws IOException {
        // Given
        String init = Files.readString(REPOSITORY.resolve("docker/redis-cluster/init-cluster.sh"));

        // When / Then
        assertThat(init)
                .contains("noaddr")
                .contains(":0@0")
                .doesNotContain("cluster_state:ok; then\n  exit 0");
    }

    @Test
    @DisplayName("Scenario: Given the local Compose override, when Deck Read starts without an operator recovery, then it does not require the production ready marker")
    void localComposeDoesNotRequireProductionReadyMarker() throws IOException {
        // Given
        String local = Files.readString(REPOSITORY.resolve("docker-compose.local.yml"));
        String production = Files.readString(REPOSITORY.resolve("docker-compose.yml"));

        // When / Then
        assertThat(production).contains("DECK_READ_REQUIRE_READY_MARKER: \"true\"");
        assertThat(local).contains("DECK_READ_REQUIRE_READY_MARKER: \"false\"");
        String config = Files.readString(SERVICE.resolve("src/main/resources/application.properties"));
        assertThat(config)
                .contains("%prod.deck-read.read-model.require-ready-marker=${DECK_READ_REQUIRE_READY_MARKER:true}")
                .doesNotContain("%prod.deck-read.read-model.require-ready-marker=true");
    }

    @Test
    @DisplayName("Scenario: Given public Keycloak tokens and internal discovery, when Deck Read validates JWTs, then it pins the public issuer")
    void publicTokenIssuerIsExplicitWhenOidcDiscoveryUsesTheInternalRealmUrl() throws IOException {
        // Given
        String config = Files.readString(SERVICE.resolve("src/main/resources/application.properties"));
        String compose = Files.readString(REPOSITORY.resolve("docker-compose.yml"));

        // When / Then
        assertThat(config)
                .contains("quarkus.oidc.auth-server-url=${KEYCLOAK_REALM_URL:http://localhost:8080/realms/tinder}")
                .contains("quarkus.oidc.token.issuer=${KEYCLOAK_TOKEN_ISSUER:https://auth.misyk.tech/realms/spring}");
        assertThat(compose)
                .contains("KEYCLOAK_REALM_URL: ${KEYCLOAK_REALM_URL:-http://keycloak:9080/realms/spring}")
                .contains("QUARKUS_OIDC_TOKEN_ISSUER: ${KEYCLOAK_TOKEN_ISSUER:-https://auth.misyk.tech/realms/spring}");
    }

    @Test
    @DisplayName("Scenario: Given JWT user identity, when Deck Read resolves a viewer, then Deck access uses the locally mapped profile identity")
    void jwtUserIdentityIsMappedLocallyBeforeProfileKeyedDeckAccess() throws IOException {
        // Given
        String main = javaSources(SERVICE.resolve("src/main/java"));

        // When / Then
        assertThat(main).contains("viewerUserId", "viewerProfileId");
        assertThat(main).doesNotContain("profileIdByUser");
    }

    @Test
    @DisplayName("Scenario: Given projection events, when Deck Read materializes them, then profile, swipe, match and recovery boundaries exist")
    void eventMaterializersAndRecoveryReadinessArePresent() throws IOException {
        // Given
        String main = javaSources(SERVICE.resolve("src/main/java"));
        String config = Files.readString(SERVICE.resolve("src/main/resources/application.properties"));

        // When / Then
        assertThat(main)
                .contains("ProfileDeckCardProjection")
                .contains("SwipeSaved")
                .contains("MatchCreated")
                .contains("READ_MODEL_NOT_READY");
        assertThat(config)
                .contains("profile.deck-card-projection.v1")
                .contains("swipe-saved")
                .contains("match.created");
    }

    @Test
    @DisplayName("Scenario: Given the unchanged Deck service, when ownership is inspected, then it does not own Deck Read keys")
    void existingDeckServiceDoesNotOwnDeckReadKeys() throws IOException {
        // Given
        String deckMain = javaSources(REPOSITORY.resolve("services/deck/src/main/java"));
        String deckConfig = Files.readString(REPOSITORY.resolve("services/deck/src/main/resources/application.yml"));

        // When / Then
        assertThat(deckMain).doesNotContain("dr:", "read-model");
        assertThat(deckConfig).doesNotContain("dr:", "read-model");
    }

    private String javaSources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            StringBuilder source = new StringBuilder();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file)).append('\n');
            }
            return source.toString();
        }
    }
}
