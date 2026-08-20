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
                .doesNotContain("ViewerIdentityCache");
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
    @DisplayName("Scenario: Given two Redis responsibilities, when configuration is inspected, then source and clustered read-model clients are distinct")
    void sourceAndReadModelUseDistinctNamedRedisClientsAndReadModelIsClustered() throws IOException {
        // Given
        String config = Files.readString(SERVICE.resolve("src/main/resources/application.properties"));

        // When / Then
        assertThat(config)
                .contains("quarkus.redis.deck-source.hosts")
                .contains("quarkus.redis.read-model.hosts")
                .contains("quarkus.redis.read-model.client-type=cluster");
    }

    @Test
    @DisplayName("Scenario: Given production Deck Read roles, when Redis Cluster seed hosts are configured, then every URI is a contiguous comma-separated value")
    void productionRedisClusterSeedHostsDoNotContainYamlFoldedWhitespace() throws IOException {
        // Given
        String compose = Files.readString(REPOSITORY.resolve("docker-compose.yml"));

        // When / Then
        assertThat(compose)
                .contains("DECK_READ_REDIS_HOSTS: redis://deck-read-redis-1:6379,redis://deck-read-redis-2:6379,redis://deck-read-redis-3:6379,redis://deck-read-redis-4:6379,redis://deck-read-redis-5:6379,redis://deck-read-redis-6:6379")
                .doesNotContain("DECK_READ_REDIS_HOSTS: >-");
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
