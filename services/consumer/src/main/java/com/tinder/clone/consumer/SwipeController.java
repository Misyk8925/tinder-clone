package com.tinder.clone.consumer;

import com.tinder.clone.consumer.model.dto.LikedMeDto;
import com.tinder.clone.consumer.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService service;

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
     * The Gateway injects X-User-Id after validating the JWT.
     */
    @GetMapping("/api/v1/swipes/liked-me")
    public List<LikedMeDto> getLikedMe(@RequestHeader("X-User-Id") UUID profileId) {
        log.debug("Liked-me request for profileId={}", profileId);
        return service.getLikedMe(profileId);
    }
}
