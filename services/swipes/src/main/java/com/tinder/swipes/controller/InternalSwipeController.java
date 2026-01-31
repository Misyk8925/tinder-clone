package com.tinder.swipes.controller;

import com.tinder.swipes.service.SwipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal API endpoints for service-to-service communication
 * These endpoints are not secured (see SecurityConfig)
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalSwipeController {

    private final SwipeService service;

    /**
     * Batch check if swipes exist between viewer and list of candidates
     * Used by deck service to filter out already swiped profiles
     */
    @PostMapping(value = "/between/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<UUID, Boolean> betweenBatch(@RequestParam("viewerId") UUID viewerId,
                                           @RequestBody List<UUID> candidateIds) {
        log.debug("Internal batch swipe check: viewerId={}, candidateCount={}", viewerId, candidateIds.size());
        return service.existsBetweenBatch(viewerId, candidateIds);
    }
}
