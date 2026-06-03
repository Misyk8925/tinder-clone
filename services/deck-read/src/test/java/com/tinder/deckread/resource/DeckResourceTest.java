package com.tinder.deckread.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.dto.DeckEntry;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.client.ProfilesClient;
import com.tinder.deckread.redis.DeckKeySchema;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end test of the {@code GET /api/v1/deck} endpoint over HTTP: JWT auth (viewerId from the
 * {@code sub} claim) and the full read → hydrate response, with the REST clients mocked and a real
 * Redis (Dev Services) backing the reader.
 */
@QuarkusTest
class DeckResourceTest {

    // Fixed viewer so the JWT sub claim (compile-time constant) matches the seeded deck.
    private static final String VIEWER = "11111111-1111-1111-1111-111111111111";

    @Inject
    RedisDataSource redis;

    @InjectMock
    @RestClient
    DeckEnsureClient ensureClient;

    @InjectMock
    @RestClient
    ProfilesClient profilesClient;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void flush() {
        redis.flushall();
    }

    private String member(UUID profileId) {
        try {
            return mapper.writeValueAsString(new DeckEntry(profileId, false));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SharedProfileDto profile(UUID id) {
        return new SharedProfileDto(id, "name-" + id, 25, null, "Berlin", true, null,
                new SharedPreferencesDto(18, 99, "ALL", 50), false, List.of(), List.of());
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        given()
                .when().get("/api/v1/deck")
                .then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = {@Claim(key = "sub", value = VIEWER)})
    void returnsHydratedProfilesInDeckOrderForAuthenticatedViewer() {
        UUID viewer = UUID.fromString(VIEWER);
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        redis.sortedSet(String.class, String.class).zadd(DeckKeySchema.deck(viewer), 10.0, member(p1)); // rank 1
        redis.sortedSet(String.class, String.class).zadd(DeckKeySchema.deck(viewer), 30.0, member(p2)); // rank 0

        when(profilesClient.getByIds(anyString()))
                .thenReturn(Uni.createFrom().item(List.of(profile(p1), profile(p2))));

        given()
                .when().get("/api/v1/deck?offset=0&limit=10")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                // serialized as "profileId" per SharedProfileDto; deck order is highest score first
                .body("profileId", contains(p2.toString(), p1.toString()));
    }

    @Test
    @TestSecurity(user = "viewer")
    @OidcSecurity(claims = {@Claim(key = "sub", value = VIEWER)})
    void emptyDeckThatCannotBeEnsuredReturnsEmptyArray() {
        // No deck seeded for this viewer; ensure reports it could not build one.
        when(ensureClient.ensure(UUID.fromString(VIEWER))).thenReturn(Uni.createFrom().item(false));

        given()
                .when().get("/api/v1/deck")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }
}
