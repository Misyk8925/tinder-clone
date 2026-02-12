package com.tinder.profiles.controller;

import com.tinder.profiles.security.JwtCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal admin endpoint for JWT cache management.
 * Should be secured or only accessible from internal network.
 */
@RestController
@RequestMapping("/internal/jwt-cache")
@RequiredArgsConstructor
@Slf4j
public class JwtCacheController {

    private final JwtCacheService jwtCacheService;

    /**
     * Get statistics about JWT cache.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        long count = jwtCacheService.getCachedTokenCount();
        return ResponseEntity.ok(Map.of(
            "cachedTokens", count,
            "status", "active"
        ));
    }

    /**
     * Clear all cached JWT tokens.
     * Forces re-validation of all tokens on next request.
     */
    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, String>> clearAllCache() {
        log.warn("Clearing all JWT cache - all tokens will be re-validated");
        jwtCacheService.clearAllTokens();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "All JWT tokens cleared from cache"
        ));
    }
}

