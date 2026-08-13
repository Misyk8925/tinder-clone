package com.tinder.deckread.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/** HTTP acceptance for v2 authentication and request validation without infrastructure. */
@QuarkusTest
@TestProfile(DeckV2ContractAcceptanceTest.NoInfrastructureProfile.class)
@Tag("acceptance")
@DisplayName("Feature: A viewer requests Deck API v2")
class DeckV2ContractAcceptanceTest {

    private static final String VIEWER_USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    @DisplayName("Scenario: Given no authenticated viewer, when the deck is requested, then UNAUTHENTICATED is returned")
    void unauthenticatedViewerIsRejected() {
        // Given no authenticated viewer
        // When the viewer requests the deck
        // Then
        given()
                .when().get("/api/v2/deck")
                .then().statusCode(401)
                .contentType("application/problem+json")
                .body("code", is("UNAUTHENTICATED"));
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given an authenticated viewer and a malformed cursor, when the deck is requested, then INVALID_CURSOR is returned")
    void malformedCursorIsRejectedAsProblemDetails() {
        // Given an authenticated viewer and a malformed cursor
        // When the viewer requests the deck
        // Then
        given()
                .queryParam("cursor", "not-a-server-cursor")
                .when().get("/api/v2/deck")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("code", is("INVALID_CURSOR"));
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given an authenticated viewer and a limit above 100, when the deck is requested, then INVALID_LIMIT is returned")
    void limitAboveOneHundredIsRejected() {
        // Given an authenticated viewer and an invalid limit
        // When the viewer requests the deck
        // Then
        given()
                .queryParam("limit", 101)
                .when().get("/api/v2/deck")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("code", is("INVALID_LIMIT"));
    }

    public static class NoInfrastructureProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.redis.devservices.enabled", "false",
                    "quarkus.redis.deck-source.devservices.enabled", "false",
                    "quarkus.redis.read-model.devservices.enabled", "false",
                    "quarkus.redis.deck-source.hosts", "redis://127.0.0.1:1",
                    "quarkus.redis.read-model.hosts", "redis://127.0.0.1:1",
                    "quarkus.redis.read-model.client-type", "standalone",
                    "deck-read.read-model.require-ready-marker", "false",
                    "quarkus.kafka.devservices.enabled", "false",
                    "quarkus.keycloak.devservices.enabled", "false");
        }
    }
}
