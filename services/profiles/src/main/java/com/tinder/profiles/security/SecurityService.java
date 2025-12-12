package com.tinder.profiles.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Security service for extracting user data from JWT tokens.
 * Provides methods for Bucket4j SpEL expressions to enable role-based rate limiting.
 *
 * Usage in application.yml:
 * - execute-condition: "@securityService.isAuthenticated()"
 * - cache-key: "@securityService.getUserId().toString()"
 * - skip-condition: "@securityService.isAdmin()"
 */
@Service("securityService")
public class SecurityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);

    private static final String ROLE_PREFIX = "ROLE_";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_PREMIUM = "PREMIUM";
    private static final String CLAIM_SUBSCRIPTION_TYPE = "subscription_type";
    private static final String CLAIM_IS_BLOCKED = "is_blocked";
    private static final String SUBSCRIPTION_PREMIUM = "PREMIUM";

    /**
     * Check if the current user is authenticated with a valid JWT token.
     *
     * @return true if user is authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && authentication instanceof JwtAuthenticationToken;
        log.debug("isAuthenticated check: {}", authenticated);
        return authenticated;
    }

    /**
     * Check if the current user is anonymous (not authenticated).
     *
     * @return true if user is anonymous, false if authenticated
     */
    public boolean isAnonymous() {
        return !isAuthenticated();
    }

    /**
     * Get the username from JWT 'sub' (subject) claim.
     *
     * @return username or null if not authenticated
     */
    public String username() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            log.debug("username: JWT is null, returning null");
            return null;
        }
        String subject = jwt.getSubject();
        log.debug("username: {}", subject);
        return subject;
    }

    /**
     * Get the user ID (UUID) from JWT 'sub' claim.
     * Handles cases where 'sub' is not a valid UUID gracefully.
     *
     * @return UUID of the user or null if not authenticated or 'sub' is not a valid UUID
     */
    public UUID getUserId() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            log.debug("getUserId: JWT is null, returning null");
            return null;
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            log.debug("getUserId: subject is null or blank");
            return null;
        }

        try {
            UUID userId = UUID.fromString(subject);
            log.debug("getUserId: {}", userId);
            return userId;
        } catch (IllegalArgumentException e) {
            log.warn("getUserId: 'sub' claim '{}' is not a valid UUID", subject);
            return null;
        }
    }

    /**
     * Get a string identifier for the current user suitable for cache keys.
     * Returns userId if available, otherwise username, otherwise "anonymous".
     *
     * @return user identifier string for cache keys
     */
    public String getUserIdentifier() {
        UUID userId = getUserId();
        if (userId != null) {
            return userId.toString();
        }
        String name = username();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "anonymous";
    }

    /**
     * Check if the current user has the specified role.
     * Handles "ROLE_" prefix automatically.
     *
     * @param role the role to check (with or without "ROLE_" prefix)
     * @return true if user has the role, false otherwise
     */
    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }

        Authentication authentication = getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("hasRole({}): not authenticated", role);
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return false;
        }

        // Normalize the role name - add ROLE_ prefix if not present
        String normalizedRole = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;

        boolean hasRole = authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equalsIgnoreCase(normalizedRole));
        log.debug("hasRole({}): {}", role, hasRole);
        return hasRole;
    }

    /**
     * Check if the current user has ADMIN role.
     *
     * @return true if user is admin, false otherwise
     */
    public boolean isAdmin() {
        boolean admin = hasRole(ROLE_ADMIN);
        log.debug("isAdmin: {}", admin);
        return admin;
    }

    /**
     * Check if the current user has Premium subscription.
     * Checks both PREMIUM role and 'subscription_type' claim.
     *
     * @return true if user has premium subscription, false otherwise
     */
    public boolean isPremium() {
        // Strategy 1: Check for PREMIUM role
        if (hasRole(ROLE_PREMIUM)) {
            log.debug("isPremium: user has PREMIUM role");
            return true;
        }

        // Strategy 2: Check for subscription_type claim
        Jwt jwt = getJwt();
        if (jwt != null) {
            Object subscriptionType = jwt.getClaim(CLAIM_SUBSCRIPTION_TYPE);
            if (subscriptionType != null && SUBSCRIPTION_PREMIUM.equalsIgnoreCase(subscriptionType.toString())) {
                log.debug("isPremium: user has PREMIUM subscription_type claim");
                return true;
            }
        }

        log.debug("isPremium: false");
        return false;
    }

    /**
     * Check if the current user is a free (non-premium) authenticated user.
     *
     * @return true if user is authenticated but not premium, false otherwise
     */
    public boolean isFree() {
        boolean free = isAuthenticated() && !isPremium();
        log.debug("isFree: {}", free);
        return free;
    }

    /**
     * Check if the current user is blocked.
     * Checks the 'is_blocked' claim in JWT.
     *
     * @return true if user is blocked, false otherwise
     */
    public boolean isBlocked() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return false;
        }

        Object isBlockedClaim = jwt.getClaim(CLAIM_IS_BLOCKED);
        if (isBlockedClaim == null) {
            return false;
        }

        boolean blocked;
        if (isBlockedClaim instanceof Boolean) {
            blocked = (Boolean) isBlockedClaim;
        } else {
            blocked = Boolean.parseBoolean(isBlockedClaim.toString());
        }

        log.debug("isBlocked: {}", blocked);
        return blocked;
    }

    /**
     * Get the user's email from JWT claims.
     *
     * @return email or null if not available
     */
    public String getEmail() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return null;
        }
        return jwt.getClaimAsString("email");
    }

    /**
     * Get a specific claim from JWT.
     *
     * @param claimName the name of the claim
     * @return claim value or null if not available
     */
    public Object getClaim(String claimName) {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return null;
        }
        return jwt.getClaim(claimName);
    }

    /**
     * Get all claims from JWT as a Map.
     *
     * @return map of claims or null if not authenticated
     */
    public Map<String, Object> getAllClaims() {
        Jwt jwt = getJwt();
        if (jwt == null) {
            return null;
        }
        return jwt.getClaims();
    }

    /**
     * Get the current Authentication from SecurityContext.
     */
    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Get the JWT from current authentication.
     *
     * @return JWT or null if not authenticated with JWT
     */
    private Jwt getJwt() {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return null;
        }

        try {
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                return jwtAuth.getToken();
            }
        } catch (ClassCastException e) {
            log.warn("Failed to cast Authentication to JwtAuthenticationToken", e);
        }

        return null;
    }
}
