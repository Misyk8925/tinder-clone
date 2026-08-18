package com.tinder.deckread.resource;

import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardPhoto;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.ProjectionSource;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@Tag("acceptance")
@DisplayName("Feature: Deck Read serves compatible v1 and generation-aware v2 responses")
class DeckResourceTest {

    private static final String VIEWER_USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID VIEWER_PROFILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Inject
    @RedisClientName("read-model")
    RedisDataSource redis;

    @Inject
    ProfileProjectionStore profiles;

    @Inject
    DeckSnapshotStore snapshots;

    @BeforeEach
    void flush() {
        redis.flushall();
        profiles.apply(event(VIEWER_PROFILE_ID, VIEWER_USER_ID)).await().indefinitely();
    }

    @Test
    @DisplayName("Scenario: Given no authenticated viewer, when v1 is requested, then the request is rejected")
    void unauthenticatedRequestIsRejected() {
        // Given no authenticated viewer
        // When / Then
        given().when().get("/api/v1/deck").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given a local snapshot, when v1 is requested, then the bare array preserves order and the legacy card shape")
    void returnsLocalProjectionInSnapshotOrder() {
        // Given
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        profiles.apply(event(p1, UUID.randomUUID().toString())).await().indefinitely();
        profiles.apply(event(p2, UUID.randomUUID().toString())).await().indefinitely();
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(p2, p1), List.of(),
                DeckState.READY, "1", Instant.now()).await().indefinitely();

        // When / Then
        given()
                .when().get("/api/v1/deck?offset=0&limit=10")
                .then().statusCode(200)
                .body("size()", is(2))
                .body("profileId", contains(p2.toString(), p1.toString()))
                .body("[0].isActive", nullValue())
                .body("[0].preferences", nullValue())
                .body("[0].photos[0].url", is("https://cdn.example.test/card.jpg"))
                .body("[0].photos[0].position", is(0))
                .body("[0].photos[0].isPrimary", is(true))
                .body("[0].photos[0].photoId", nullValue())
                .body("[0].photos[0].order", nullValue());
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given one local snapshot, when v1 and v2 are shadow-read, then their ordered profile IDs are identical")
    void v1AndV2ShadowReadsKeepTheSameCandidateOrder() {
        // Given
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        profiles.apply(event(first, UUID.randomUUID().toString())).await().indefinitely();
        profiles.apply(event(second, UUID.randomUUID().toString())).await().indefinitely();
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(second, first), List.of(),
                DeckState.READY, "shadow-source", Instant.now()).await().indefinitely();

        // When
        List<String> v1ProfileIds = given()
                .queryParam("offset", 0)
                .queryParam("limit", 100)
                .when().get("/api/v1/deck")
                .then().statusCode(200)
                .extract().jsonPath().getList("profileId", String.class);
        List<String> v2ProfileIds = given()
                .queryParam("limit", 100)
                .when().get("/api/v2/deck")
                .then().statusCode(200)
                .extract().jsonPath().getList("items.profileId", String.class);

        // Then
        org.assertj.core.api.Assertions.assertThat(v2ProfileIds)
                .containsExactlyElementsOf(v1ProfileIds);
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given no local snapshot, when v1 is requested, then its legacy response is a bare empty array")
    void missingSnapshotPreservesBareEmptyArrayContract() {
        // Given no local snapshot
        // When / Then
        given().when().get("/api/v1/deck")
                .then().statusCode(200).body("size()", is(0));
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given invalid v1 pagination, when the deck is requested, then INVALID_PAGINATION is returned")
    void invalidV1PaginationIsRejectedAsProblemDetails() {
        // Given a negative offset
        // When / Then
        given()
                .queryParam("offset", -1)
                .when().get("/api/v1/deck")
                .then().statusCode(400)
                .contentType("application/problem+json")
                .body("code", is("INVALID_PAGINATION"));
    }

    @Test
    @TestSecurity(user = "viewer-without-profile")
    @OidcSecurity(claims = @Claim(
            key = "sub", value = "99999999-9999-9999-9999-999999999999"))
    @DisplayName("Scenario: Given an available read model without a viewer projection, when v2 is requested, then BUILDING and Retry-After 2 are returned")
    void v2InitialBuildAdvertisesTwoSecondPollingWhenReadModelIsAvailable() {
        // Given an available read model without this viewer projection
        // When / Then
        given()
                .when().get("/api/v2/deck")
                .then().statusCode(202)
                .header("Retry-After", "2")
                .body("state", is("BUILDING"))
                .body("retryAfterSeconds", is(2));
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given a cursor from an older generation, when v2 is requested, then paging resets to the new generation start")
    void v2CursorResetsToStartWhenSnapshotGenerationChanges() {
        // Given
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        profiles.apply(event(first, UUID.randomUUID().toString())).await().indefinitely();
        profiles.apply(event(second, UUID.randomUUID().toString())).await().indefinitely();
        snapshots.install(VIEWER_PROFILE_ID, 0, List.of(first, second), List.of(),
                DeckState.READY, "1", Instant.now()).await().indefinitely();

        String oldCursor = given()
                .when().get("/api/v2/deck?limit=1")
                .then().statusCode(200)
                .body("items.profileId", contains(first.toString()))
                .body("generation", is(1))
                .body("cursorReset", is(false))
                .extract().path("nextCursor");

        snapshots.install(VIEWER_PROFILE_ID, 1, List.of(second, first), List.of(),
                DeckState.READY, "2", Instant.now()).await().indefinitely();

        // When / Then
        given()
                .queryParam("cursor", oldCursor)
                .queryParam("limit", 2)
                .when().get("/api/v2/deck")
                .then().statusCode(200)
                .body("items.profileId", contains(second.toString(), first.toString()))
                .body("generation", is(2))
                .body("cursorReset", is(true))
                .body("nextCursor", nullValue());
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = @Claim(key = "sub", value = VIEWER_USER_ID))
    @DisplayName("Scenario: Given a snapshot with no fresh or safely repeatable cards, when v2 is requested, then DECK_TEMPORARILY_UNAVAILABLE is returned")
    void unavailableSnapshotReturnsTemporarilyUnavailableProblem() {
        // Given the first build failed before any snapshot was installed
        snapshots.markUnavailable(VIEWER_PROFILE_ID).await().indefinitely();

        // When / Then
        given()
                .when().get("/api/v2/deck")
                .then().statusCode(503)
                .contentType("application/problem+json")
                .body("code", is("DECK_TEMPORARILY_UNAVAILABLE"));
    }

    private ProfileDeckCardProjectionEvent event(UUID profileId, String userId) {
        return new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), profileId, userId, 1, Instant.now(),
                ProfileProjectionOperation.UPSERT, ProjectionSource.LIVE, null,
                new DeckCardProjection(
                        profileId, "name-" + profileId, 25, "Berlin", "bio", true,
                        new DeckCardPreferences(18, 99, "ALL", 50),
                        List.of(new DeckCardPhoto(
                                UUID.randomUUID(), "https://cdn.example.test/card.jpg", 0)),
                        List.of()));
    }
}
