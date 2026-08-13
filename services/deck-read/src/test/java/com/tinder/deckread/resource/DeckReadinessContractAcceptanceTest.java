package com.tinder.deckread.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(DeckReadinessContractAcceptanceTest.LostReadModelProfile.class)
@Tag("acceptance")
@DisplayName("Feature: Deck APIs fail safely while the read model is unavailable")
class DeckReadinessContractAcceptanceTest {

    private static final String VIEWER_USER_ID = "11111111-1111-1111-1111-111111111111";

    @ParameterizedTest(name = "Scenario: Given the Read Cluster is unavailable, when {0} is requested, then READ_MODEL_NOT_READY is returned")
    @ValueSource(strings = {"/api/v1/deck", "/api/v2/deck"})
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    void lostReadClusterReturnsNotReadyForBothApiVersionsInsteadOfFalseEmpty(String apiPath) {
        // Given the Read Cluster is unavailable
        // When the authenticated viewer requests either supported Deck API version
        // Then
        given().when().get(apiPath)
                .then().statusCode(503)
                .contentType("application/problem+json")
                .body("code", is("READ_MODEL_NOT_READY"));
    }

    public static class LostReadModelProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "deck-read.read-model.require-ready-marker", "true",
                    "quarkus.redis.devservices.enabled", "false",
                    "quarkus.redis.deck-source.devservices.enabled", "false",
                    "quarkus.redis.read-model.devservices.enabled", "false",
                    "quarkus.redis.deck-source.hosts", "redis://127.0.0.1:1",
                    "quarkus.redis.read-model.hosts", "redis://127.0.0.1:1",
                    "quarkus.redis.read-model.client-type", "standalone",
                    "quarkus.kafka.devservices.enabled", "false",
                    "quarkus.keycloak.devservices.enabled", "false");
        }
    }
}
