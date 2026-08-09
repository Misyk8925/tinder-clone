package com.tinder.gateway;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRouteConfigurationTest {

    @Test
    void routesThePublicDeckEndpointDirectlyToDeckRead() {
        Map<String, Object> deckRead = routes().stream()
                .filter(route -> "deck-read".equals(route.get("id")))
                .findFirst()
                .orElseThrow();

        assertThat(deckRead.get("uri"))
                .isEqualTo("${DECK_READ_SERVICE_URL:http://localhost:8040}");
        assertThat(asStringList(deckRead.get("predicates")))
                .containsExactly("Path=/api/v1/deck", "Method=GET");
        assertThat(asStringList(deckRead.get("filters")))
                .doesNotContain("RewritePath");
    }

    @Test
    void noRouteExposesTheLegacyProfilesDeckPath() {
        assertThat(routes().toString()).doesNotContain("/api/v1/profiles/deck");
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
    private List<String> asStringList(Object value) {
        return value == null ? List.of() : (List<String>) value;
    }
}
