package com.example.swipes_demo;

import com.example.swipes_demo.profileCache.ProfileCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

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

    public Mono<Void> sendSwipe(SwipeDto dto, boolean isPremiumOrAdmin, Jwt jwt, boolean internalRequest) {
        if (Boolean.TRUE.equals(dto.isSuper()) && !isPremiumOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super like requires a premium or admin account");
        }

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

        Mono<Boolean> profilesExist = internalRequest && internalBypassProfileCheck
                ? Mono.just(true)
                : profileCacheService.existsAll(profile1Id, profile2Id, bearerToken);

        return profilesExist
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
