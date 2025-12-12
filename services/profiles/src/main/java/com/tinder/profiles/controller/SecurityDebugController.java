package com.tinder.profiles.controller;

import com.tinder.profiles.security.SecurityService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug controller for testing security and rate limiting.
 *
 * IMPORTANT: This controller is only active in the "dev" profile.
 * Remove or disable before deploying to production.
 *
 * To enable: add "dev" to spring.profiles.active in application.yml
 * or use: java -Dspring.profiles.active=dev -jar app.jar
 */
@RestController
@RequestMapping("/api/v1/debug")
@Profile("dev")
public class SecurityDebugController {

    private final SecurityService securityService;

    public SecurityDebugController(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Returns current user's security information extracted from JWT.
     * Useful for debugging authentication and role issues.
     *
     * @return Map containing user security information
     */
    @GetMapping("/security-info")
    public ResponseEntity<Map<String, Object>> getSecurityInfo() {
        Map<String, Object> info = new HashMap<>();

        // Authentication status
        info.put("isAuthenticated", securityService.isAuthenticated());
        info.put("isAnonymous", securityService.isAnonymous());

        // User identification
        info.put("username", securityService.username());
        info.put("userId", securityService.getUserId());
        info.put("userIdentifier", securityService.getUserIdentifier());
        info.put("email", securityService.getEmail());

        // Role checks
        info.put("isAdmin", securityService.isAdmin());
        info.put("isPremium", securityService.isPremium());
        info.put("isFree", securityService.isFree());
        info.put("isBlocked", securityService.isBlocked());

        // Common role checks
        info.put("hasRole_ADMIN", securityService.hasRole("ADMIN"));
        info.put("hasRole_PREMIUM", securityService.hasRole("PREMIUM"));
        info.put("hasRole_USER", securityService.hasRole("USER"));

        // JWT claims (if authenticated)
        if (securityService.isAuthenticated()) {
            Map<String, Object> claims = securityService.getAllClaims();
            if (claims != null) {
                // Only include safe claims for debugging
                Map<String, Object> safeClaims = new HashMap<>();
                safeClaims.put("sub", claims.get("sub"));
                safeClaims.put("email", claims.get("email"));
                safeClaims.put("preferred_username", claims.get("preferred_username"));
                safeClaims.put("realm_access", claims.get("realm_access"));
                safeClaims.put("resource_access", claims.get("resource_access"));
                safeClaims.put("subscription_type", claims.get("subscription_type"));
                safeClaims.put("is_blocked", claims.get("is_blocked"));
                safeClaims.put("exp", claims.get("exp"));
                safeClaims.put("iat", claims.get("iat"));
                info.put("jwtClaims", safeClaims);
            }
        }

        return ResponseEntity.ok(info);
    }

    /**
     * Simple endpoint for testing rate limits.
     * Each call increments towards your rate limit.
     *
     * @return Success message with remaining calls info
     */
    @GetMapping("/rate-limit-test")
    public ResponseEntity<Map<String, Object>> rateLimitTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Rate limit test endpoint hit successfully");
        response.put("userType", getUserType());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to determine user type for rate limit testing.
     */
    private String getUserType() {
        if (securityService.isAdmin()) {
            return "ADMIN";
        } else if (securityService.isBlocked()) {
            return "BLOCKED";
        } else if (securityService.isPremium()) {
            return "PREMIUM";
        } else if (securityService.isFree()) {
            return "FREE";
        } else {
            return "ANONYMOUS";
        }
    }
}
