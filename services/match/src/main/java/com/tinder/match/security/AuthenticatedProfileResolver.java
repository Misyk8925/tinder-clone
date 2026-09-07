package com.tinder.match.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/** Resolves the authenticated Keycloak subject to its server-owned profile id. */
@Service
public class AuthenticatedProfileResolver {
    private final UserProfileMappingService mappings;
    private final RestClient profilesClient;

    public AuthenticatedProfileResolver(UserProfileMappingService mappings,
            RestClient.Builder builder,
            @Value("${services.profiles.base-url:http://localhost:8010}") String profilesBaseUrl) {
        this.mappings = mappings;
        this.profilesClient = builder.baseUrl(profilesBaseUrl).build();
    }

    public UUID resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Authenticated profile is required");
        }
        UUID cached = mappings.resolve(jwt.getSubject());
        if (cached != null) return cached;
        ProfileResponse profile = profilesClient.get().uri("/api/v1/profiles/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                .retrieve().body(ProfileResponse.class);
        if (profile == null || profile.profileId() == null) {
            throw new AccessDeniedException("Authenticated user has no active profile");
        }
        mappings.register(jwt.getSubject(), profile.profileId());
        return profile.profileId();
    }

    record ProfileResponse(UUID profileId) {}
}
