package com.tinder.match.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The chat identity must come from the token, not from the caller. These tests pin the shape
 * of that lookup: it is made against the profiles service with the caller's own bearer token,
 * so the only profile it can ever return is the caller's.
 */
class UserProfileMappingServiceTest {

    private static final String PROFILES_URL = "http://profiles:8010";
    private static final String ME_ENDPOINT = PROFILES_URL + "/api/v1/profiles/me";

    private MockRestServiceServer profiles;
    private UserProfileMappingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PROFILES_URL);
        profiles = MockRestServiceServer.bindTo(builder).build();
        service = new UserProfileMappingService(builder.build());
    }

    @Test
    @DisplayName("the profile is read from /me using the caller's own token")
    void resolvesProfileFromCallersToken() {
        UUID profileId = UUID.randomUUID();
        profiles.expect(requestTo(ME_ENDPOINT))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer caller-token"))
                .andRespond(withSuccess("{\"profileId\":\"" + profileId + "\"}", MediaType.APPLICATION_JSON));

        assertThat(service.resolve("keycloak-sub", "caller-token")).isEqualTo(profileId);
        profiles.verify();
    }

    @Test
    @DisplayName("a resolved profile is cached instead of re-fetched on every message")
    void cachesResolvedProfile() {
        UUID profileId = UUID.randomUUID();
        profiles.expect(requestTo(ME_ENDPOINT))
                .andRespond(withSuccess("{\"profileId\":\"" + profileId + "\"}", MediaType.APPLICATION_JSON));

        assertThat(service.resolve("keycloak-sub", "caller-token")).isEqualTo(profileId);
        assertThat(service.resolve("keycloak-sub", "caller-token")).isEqualTo(profileId);

        // A second upstream call would fail the single-expectation server.
        profiles.verify();
    }

    @Test
    @DisplayName("no token means no identity")
    void refusesToResolveWithoutToken() {
        assertThat(service.resolve("keycloak-sub", null)).isNull();
        assertThat(service.resolve("keycloak-sub", "  ")).isNull();
        assertThat(service.resolve(null, "caller-token")).isNull();
        profiles.verify();
    }

    @Test
    @DisplayName("an upstream failure yields no identity rather than a guess")
    void failsClosedWhenProfilesIsUnavailable() {
        profiles.expect(requestTo(ME_ENDPOINT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(service.resolve("keycloak-sub", "caller-token")).isNull();
        profiles.verify();
    }
}
