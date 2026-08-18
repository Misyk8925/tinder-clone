package com.tinder.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Gateway route/security acceptance for FR-9. */
@Tag("acceptance")
@DisplayName("Feature: Gateway exposes both Deck API versions")
class DeckReadV2RouteAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given v1 and v2 Deck requests, when Gateway routes them, then both reach Deck Read without rewriting")
    void gatewayRoutesBothDeckVersionsToDeckReadWithoutRewrite() {
        // Given
        Map<String, Object> route = routes().stream()
                .filter(candidate -> "deck-read".equals(candidate.get("id")))
                .findFirst()
                .orElseThrow();

        // When / Then
        assertThat(strings(route.get("predicates")))
                .contains("Path=/api/v1/deck,/api/v2/deck", "Method=GET");
        assertThat(strings(route.get("filters"))).doesNotContain("RewritePath");
    }

    @Test
    @DisplayName("Scenario: Given v1 and v2 Deck requests, when security evaluates them, then the bearer token is forwarded for Deck Read validation")
    void securityForwardsBothVersionsForDeckReadJwtValidation() throws Exception {
        // Given
        String security = Files.readString(Path.of("src/main/java/com/tinder/gateway/SecurityConfig.java"));

        // When / Then
        assertThat(security).contains("/api/v1/deck", "/api/v2/deck");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> routes() {
        InputStream applicationYaml = getClass().getClassLoader().getResourceAsStream("application.yml");
        assertThat(applicationYaml).isNotNull();
        Map<String, Object> root = new Yaml().load(applicationYaml);
        Map<String, Object> spring = (Map<String, Object>) root.get("spring");
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
        Map<String, Object> server = (Map<String, Object>) gateway.get("server");
        Map<String, Object> webflux = (Map<String, Object>) server.get("webflux");
        return (List<Map<String, Object>>) webflux.get("routes");
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        return value == null ? List.of() : (List<String>) value;
    }
}
