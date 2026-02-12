package com.tinder.swipes.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Service for managing JWT cache operations.
 * Provides methods to invalidate cached tokens when needed (e.g., on logout, token revocation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtCacheService {

    private static final String JWT_CACHE_PREFIX = "jwt:cache:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Clear all JWT tokens from cache.
     * Use with caution - forces re-validation of all tokens.
     */
    public void clearAllTokens() {
        Set<String> keys = redisTemplate.keys(JWT_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            Long deleted = redisTemplate.delete(keys);
            log.info("Cleared {} JWT tokens from cache", deleted);
        }
    }

    /**
     * Clear specific token from cache by token string.
     * Useful for logout or token revocation.
     *
     * @param token the JWT token string to invalidate
     */
    public void invalidateToken(String token) {
        String cacheKey = JWT_CACHE_PREFIX + hashToken(token);
        Boolean deleted = redisTemplate.delete(cacheKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Invalidated JWT token from cache");
        }
    }

    /**
     * Get count of cached tokens.
     *
     * @return number of tokens currently cached
     */
    public long getCachedTokenCount() {
        Set<String> keys = redisTemplate.keys(JWT_CACHE_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    /**
     * Generate a hash of the token for use as cache key.
     * Must match the hashing in CachedJwtDecoder.
     */
    private String hashToken(String token) {
        int hash = token.hashCode();
        String suffix = token.length() > 20 ? token.substring(token.length() - 20) : token;
        return hash + ":" + suffix.hashCode();
    }
}

