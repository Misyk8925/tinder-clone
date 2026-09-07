package com.tinder.clone.consumer;

import com.tinder.clone.consumer.model.dto.LikedMeDto;
import com.tinder.clone.consumer.security.CallerProfileResolver;
import com.tinder.clone.consumer.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService service;
    private final CallerProfileResolver callerProfileResolver;

    /**
     * Batch check if swipes exist between viewer and list of candidates.
     * Used by deck service to filter out already swiped profiles (mTLS protected, port 8051).
     */
    @PostMapping(value = "/between/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<UUID, Boolean> betweenBatch(@RequestParam("viewerId") UUID viewerId,
                                           @RequestBody List<UUID> candidateIds) {
        log.debug("Internal batch swipe check: viewerId={}, candidateCount={}", viewerId, candidateIds.size());
        return service.existsBetweenBatch(viewerId, candidateIds);
    }

    /**
     * Returns profiles that have liked the authenticated user but whom the user hasn't swiped yet.
     * Premium/admin only — enforced at the Gateway via PremiumOrAdminFilter.
     * <p>
     * The profile is resolved from the caller's own token rather than read from the
     * gateway-injected {@code X-User-Id} header: that header is only trustworthy on the gateway
     * path, and a caller reaching this service directly could otherwise name any profile and read
     * who liked them.
     */
    @GetMapping("/api/v1/swipes/liked-me")
    public ResponseEntity<List<LikedMeDto>> getLikedMe(@AuthenticationPrincipal Jwt jwt) {
        UUID profileId = callerProfileResolver.resolve(jwt);
        if (profileId == null) {
            log.warn("Liked-me request rejected: no profile resolved for the authenticated caller");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.debug("Liked-me request for profileId={}", profileId);
        return ResponseEntity.ok(service.getLikedMe(profileId));
    }
}
