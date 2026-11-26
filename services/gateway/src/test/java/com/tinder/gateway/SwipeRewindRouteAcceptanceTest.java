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

/** Gateway route, authz, and rate-limit acceptance for FR-1, NFR-4, and HTTP 401/429. */
@Tag("acceptance")
@DisplayName("Feature: Gateway exposes POST /api/v1/swipes/rewind to all authenticated users with rate limiting (FR-1, NFR-4)")
class SwipeRewindRouteAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given an authenticated rewind request, when Gateway routes it, then POST /api/v1/swipes/rewind reaches swipes-go without PremiumOrAdminFilter")
    void gatewayRoutesRewindToSwipesServiceWithoutPremiumGate() {
        // Given / When
        Map<String, Object> route = routes().stream()
                .filter(candidate -> "swipes-rewind".equals(candidate.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected route id 'swipes-rewind' in gateway application.yml"));

        // Then
        assertThat(route.get("uri")).isEqualTo("${SWIPES_SERVICE_URL:http://localhost:8040}");
        assertThat(strings(route.get("predicates")))
                .contains("Path=/api/v1/swipes/rewind", "Method=POST");
        assertThat(filterNames(route.get("filters")))
                .contains("RoleBasedRateLimitFilter")
                .doesNotContain("PremiumOrAdminFilter");
    }

    @Test
    @DisplayName("Scenario: Given role-based rate limits on rewind, when Gateway configures RoleBasedRateLimitFilter, then basic=100/h, premium=500/h, and anon=0 (429 RATE_LIMITED)")
    @SuppressWarnings("unchecked")
    void gatewayAppliesRoleBasedRateLimitsToRewind() {
        // Given
        Map<String, Object> route = routes().stream()
                .filter(candidate -> "swipes-rewind".equals(candidate.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected route id 'swipes-rewind' in gateway application.yml"));

        // When
        Map<String, Object> rateLimitFilter = ((List<Object>) route.get("filters")).stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .filter(item -> "RoleBasedRateLimitFilter".equals(item.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected RoleBasedRateLimitFilter on swipes-rewind route"));
        Map<String, Object> args = (Map<String, Object>) rateLimitFilter.get("args");

        // Then
        assertThat(args).isNotNull();
        assertThat(args.get("basicCapacity")).isEqualTo(100);
        assertThat(args.get("premiumCapacity")).isEqualTo(500);
        assertThat(args.get("anonCapacity")).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario: Given an unauthenticated call to /api/v1/swipes/rewind, when Gateway security evaluates it, then /api/** requires a valid JWT (401 UNAUTHENTICATED)")
    void securityRequiresJwtForRewindPath() throws Exception {
        // Given
        String security = Files.readString(Path.of("src/main/java/com/tinder/gateway/SecurityConfig.java"));

        // When / Then
        assertThat(security).contains(".pathMatchers(\"/api/**\").authenticated()");
        assertThat(security).doesNotContain("/api/v1/swipes/rewind");
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

    @SuppressWarnings("unchecked")
    private List<String> filterNames(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<Object>) value).stream()
                .map(item -> item instanceof Map<?, ?> map ? String.valueOf(map.get("name")) : String.valueOf(item))
                .toList();
    }
}
