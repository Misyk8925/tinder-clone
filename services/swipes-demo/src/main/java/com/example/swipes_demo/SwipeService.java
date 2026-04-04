package com.example.swipes_demo;

import com.example.swipes_demo.profileCache.ProfileCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SwipeService {

    private final SwipeProducer swipeProducer;
    private final ProfileCacheService profileCacheService;

    public Mono<Void> sendSwipe(SwipeDto dto, boolean isPremiumOrAdmin, Jwt jwt) {
        String bearerToken = extractBearerToken(jwt);
        UUID profile1Id = parseProfileId(dto.profile1Id(), "profile1Id");
        UUID profile2Id = parseProfileId(dto.profile2Id(), "profile2Id");

        if (profile1Id.equals(profile2Id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile1Id and profile2Id must be different");
        }

        if (Boolean.TRUE.equals(dto.isSuper()) && !isPremiumOrAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super like requires a premium or admin account");
        }

        return profileCacheService.existsAll(profile1Id, profile2Id, bearerToken)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "One or both profiles were not found"
                        ));
                    }

                    SwipeCreatedEvent event = SwipeCreatedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .profile1Id(dto.profile1Id())
                            .profile2Id(dto.profile2Id())
                            .decision(dto.decision())
                            .isSuper(Boolean.TRUE.equals(dto.isSuper()))
                            .timestamp(System.currentTimeMillis())
                            .build();

                    return swipeProducer.send(event);
                });
    }

    private String extractBearerToken(Jwt jwt) {
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
