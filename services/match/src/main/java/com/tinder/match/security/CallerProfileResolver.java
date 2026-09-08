package com.tinder.match.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

/**
 * Single place where "who is calling" is decided for both the REST and the STOMP entry points.
 * <p>
 * Every handler that touches conversation or match data must obtain the acting profile ID from
 * here rather than from a request parameter, path variable or message payload — those are
 * attacker-controlled and using them for identity is what turns a read into an IDOR.
 */
@Component
@RequiredArgsConstructor
public class CallerProfileResolver {

    private final UserProfileMappingService userProfileMappingService;

    /** Resolves the profile owned by the JWT-authenticated caller of an HTTP request. */
    public UUID requireProfileId(Jwt jwt) {
        if (jwt == null) {
            throw new UnknownCallerProfileException("Authentication is required");
        }
        return require(userProfileMappingService.resolve(jwt.getSubject(), jwt.getTokenValue()));
    }

    /** Resolves the profile owned by the caller of a STOMP frame. */
    public UUID requireProfileId(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new UnknownCallerProfileException("Authenticated sender is required");
        }
        Jwt token = jwtAuthentication.getToken();
        return require(userProfileMappingService.resolve(token.getSubject(), token.getTokenValue()));
    }

    private UUID require(UUID profileId) {
        if (profileId == null) {
            throw new UnknownCallerProfileException(
                    "No profile is associated with the authenticated user");
        }
        return profileId;
    }

    /** Raised when the caller is authenticated but owns no resolvable profile. */
    public static class UnknownCallerProfileException extends RuntimeException {
        public UnknownCallerProfileException(String message) {
            super(message);
        }
    }
}
