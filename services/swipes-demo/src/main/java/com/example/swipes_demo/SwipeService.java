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

    private Mono<Void> enqueueSwipe(SwipeDto dto) {
        SwipeCreatedEvent event = new SwipeCreatedEvent(
                nextEventId(),
                dto.profile1Id(),
                dto.profile2Id(),
                dto.decision(),
                Boolean.TRUE.equals(dto.isSuper()),
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
}
