package com.example.swipes_demo;

import com.example.swipes_demo.profileCache.ProfileCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class SwipeService {

    private final SwipeProducer swipeProducer;
    private final ProfileCacheService profileCacheService;
    private final AtomicLong eventSequence = new AtomicLong(System.nanoTime());

    @Value("${swipes.internal-bypass-profile-check:false}")
    private boolean internalBypassProfileCheck;

    public Mono<Void> sendSwipe(SwipeDto dto, boolean isPremiumOrAdmin, Jwt jwt) {
        return sendSwipe(dto, isPremiumOrAdmin, jwt, false);
    }

    public Mono<Void> sendSwipe(SwipeDto dto, boolean superRoute, Jwt jwt, boolean internalRequest) {
        // The gateway also gates /super with PremiumOrAdminFilter, but this service must not
        // depend on being reached through it: re-check the entitlement from the caller's own
        // token so a direct call cannot buy a super like for free.
        boolean privileged = internalRequest || hasPremiumOrAdminRole(jwt);
        if ((superRoute || Boolean.TRUE.equals(dto.isSuper())) && !privileged) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super like requires a premium or admin account");
        }
        boolean isPremiumOrAdmin = superRoute || privileged;

        boolean trustedBenchmarkRequest = internalRequest && internalBypassProfileCheck;
        if (trustedBenchmarkRequest) {
            if (dto.profile1Id().equals(dto.profile2Id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile1Id and profile2Id must be different");
            }
            return enqueueSwipe(dto);
        }

        String bearerToken = extractBearerToken(jwt, internalRequest);
        UUID profile1Id = parseProfileId(dto.profile1Id(), "profile1Id");
        UUID profile2Id = parseProfileId(dto.profile2Id(), "profile2Id");

        if (profile1Id.equals(profile2Id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile1Id and profile2Id must be different");
        }

        // Deferred so the existence lookup only runs once ownership has been established.
        Mono<Boolean> profilesExist = Mono.defer(() -> internalRequest && internalBypassProfileCheck
                ? Mono.just(true)
                : profileCacheService.existsAll(profile1Id, profile2Id, bearerToken));

        return requireOwnership(profile1Id, bearerToken, internalRequest)
                .then(profilesExist)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "One or both profiles were not found"
                        ));
                    }

                    return enqueueSwipe(dto);
                });
    }

    /**
     * A swipe may only be recorded as the caller's own profile. Without this the swiper
     * identity is whatever {@code profile1Id} the request body claims, which lets anyone
     * like or pass on other people's behalf — and so manufacture matches for them.
     * Trusted internal (benchmark) traffic is exempt; it carries no user token.
     */
    private Mono<Void> requireOwnership(UUID profile1Id, String bearerToken, boolean internalRequest) {
        if (internalRequest) {
            return Mono.empty();
        }
        return profileCacheService.profileIdForToken(bearerToken)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Profile ownership could not be verified")))
                .flatMap(ownerId -> ownerId.equals(profile1Id)
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "profile1Id does not belong to the authenticated user")))
                .then();
    }

    public Mono<Void> sendTrustedInternalSwipe(String body, boolean isPremiumOrAdmin) {
        ParsedSwipe parsedSwipe = parseTrustedInternalSwipe(body);
        if (parsedSwipe.isSuper() && !isPremiumOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super like requires a premium or admin account");
        }

        if (parsedSwipe.profile1Id().equals(parsedSwipe.profile2Id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile1Id and profile2Id must be different");
        }

        return enqueueSwipe(
                parsedSwipe.profile1Id(),
                parsedSwipe.profile2Id(),
                parsedSwipe.decision(),
                parsedSwipe.isSuper()
        );
    }

    private Mono<Void> enqueueSwipe(SwipeDto dto) {
        return enqueueSwipe(
                dto.profile1Id(),
                dto.profile2Id(),
                dto.decision(),
                Boolean.TRUE.equals(dto.isSuper())
        );
    }

    private Mono<Void> enqueueSwipe(String profile1Id, String profile2Id, boolean decision, boolean isSuper) {
        SwipeCreatedEvent event = new SwipeCreatedEvent(
                nextEventId(),
                profile1Id,
                profile2Id,
                decision,
                isSuper,
                System.currentTimeMillis()
        );

        return swipeProducer.send(event);
    }

    private String nextEventId() {
        return new UUID(System.currentTimeMillis(), eventSequence.incrementAndGet()).toString();
    }

    /** Reads Keycloak realm/resource roles off the verified token. */
    @SuppressWarnings("unchecked")
    private boolean hasPremiumOrAdminRole(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        Set<String> roles = new HashSet<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> realmRoles) {
            realmRoles.forEach(role -> roles.add(String.valueOf(role).toUpperCase(Locale.ROOT)));
        }
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(claims -> {
                if (claims instanceof Map<?, ?> claimsMap
                        && claimsMap.get("roles") instanceof Collection<?> resourceRoles) {
                    resourceRoles.forEach(role -> roles.add(String.valueOf(role).toUpperCase(Locale.ROOT)));
                }
            });
        }
        return roles.contains("USER_PREMIUM") || roles.contains("ADMIN");
    }

    private String extractBearerToken(Jwt jwt, boolean internalRequest) {
        if (internalRequest && (jwt == null || jwt.getTokenValue() == null || jwt.getTokenValue().isBlank())) {
            return null;
        }

        if (jwt == null || jwt.getTokenValue() == null || jwt.getTokenValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing JWT principal");
        }
        return jwt.getTokenValue();
    }

    private UUID parseProfileId(String rawId, String fieldName) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid UUID in field: " + fieldName
            );
        }
    }

    private ParsedSwipe parseTrustedInternalSwipe(String body) {
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Swipe body is required");
        }

        return new ParsedSwipe(
                extractString(body, "profile1Id"),
                extractString(body, "profile2Id"),
                extractBoolean(body, "decision", false),
                extractBoolean(body, "isSuper", false)
        );
    }

    private String extractString(String body, String fieldName) {
        String marker = "\"" + fieldName + "\"";
        int fieldStart = body.indexOf(marker);
        if (fieldStart < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing field: " + fieldName);
        }

        int colon = body.indexOf(':', fieldStart + marker.length());
        if (colon < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field: " + fieldName);
        }

        int valueStart = skipWhitespace(body, colon + 1);
        if (valueStart >= body.length() || body.charAt(valueStart) != '"') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field: " + fieldName);
        }

        int valueEnd = body.indexOf('"', valueStart + 1);
        if (valueEnd < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field: " + fieldName);
        }

        String value = body.substring(valueStart + 1, valueEnd);
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing field: " + fieldName);
        }
        return value;
    }

    private boolean extractBoolean(String body, String fieldName, boolean defaultValue) {
        String marker = "\"" + fieldName + "\"";
        int fieldStart = body.indexOf(marker);
        if (fieldStart < 0) {
            return defaultValue;
        }

        int colon = body.indexOf(':', fieldStart + marker.length());
        if (colon < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field: " + fieldName);
        }

        int valueStart = skipWhitespace(body, colon + 1);
        if (body.startsWith("true", valueStart)) {
            return true;
        }
        if (body.startsWith("false", valueStart)) {
            return false;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid field: " + fieldName);
    }

    private int skipWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private record ParsedSwipe(String profile1Id, String profile2Id, boolean decision, boolean isSuper) {
    }
}
