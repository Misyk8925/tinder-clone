package com.tinder.profiles.redis;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Resilient wrapper for Redis cache operations with circuit breaker, bulkhead, and retry patterns.
 * Provides fail-safe cache operations that degrade gracefully when Redis is unavailable.
 */
@Component
@Slf4j
public class ResilientCacheManager {

    private final CacheManager cacheManager;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Retry retry;

    public ResilientCacheManager(
            CacheManager cacheManager,
            @Qualifier("redisCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("redisBulkhead") Bulkhead bulkhead,
            @Qualifier("redisRetry") Retry retry
    ) {
        this.cacheManager = cacheManager;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.retry = retry;
    }

    /**
     * Get value from cache with resilience patterns applied
     *
     * @param cacheName name of the cache
     * @param key cache key
     * @return cached value wrapper, or null if cache unavailable or key not found
     */
    public Cache.ValueWrapper get(String cacheName, Object key) {
        return executeResilient(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found", cacheName);
                return null;
            }
            return cache.get(key);
        }, "get", cacheName, key);
    }

    /**
     * Put value into cache with resilience patterns applied
     *
     * @param cacheName name of the cache
     * @param key cache key
     * @param value value to cache
     */
    public void put(String cacheName, Object key, Object value) {
        executeResilient(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, skipping put operation", cacheName);
                return null;
            }
            cache.put(key, value);
            return null;
        }, "put", cacheName, key);
    }

    /**
     * Evict value from cache with resilience patterns applied
     *
     * @param cacheName name of the cache
     * @param key cache key to evict
     */
    public void evict(String cacheName, Object key) {
        executeResilient(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, skipping evict operation", cacheName);
                return null;
            }
            cache.evict(key);
            return null;
        }, "evict", cacheName, key);
    }

    /**
     * Clear entire cache with resilience patterns applied
     *
     * @param cacheName name of the cache to clear
     */
    public void clear(String cacheName) {
        executeResilient(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                log.warn("Cache '{}' not found, skipping clear operation", cacheName);
                return null;
            }
            cache.clear();
            return null;
        }, "clear", cacheName, null);
    }

    /**
     * Execute cache operation with circuit breaker, bulkhead, and retry
     *
     * @param supplier the cache operation to execute
     * @param operation operation name for logging
     * @param cacheName cache name for logging
     * @param key cache key for logging
     * @return result of the operation, or null if failed
     */
    private <T> T executeResilient(Supplier<T> supplier, String operation, String cacheName, Object key) {
        try {
            // Decorate supplier with resilience patterns
            // Order: Retry(Bulkhead(CircuitBreaker(operation)))
            // This ensures bulkhead is acquired per retry attempt, not for all attempts combined
            Supplier<T> cbDecorated = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
            Supplier<T> bulkheadDecorated = Bulkhead.decorateSupplier(bulkhead, cbDecorated);
            Supplier<T> retryDecorated = Retry.decorateSupplier(retry, bulkheadDecorated);

            return retryDecorated.get();

        } catch (Exception e) {
            // Log but don't throw - fail-open strategy for cache
            log.error("Redis cache operation '{}' failed for cache '{}' key '{}': {} - {}. " +
                    "Continuing without cache (fail-open).",
                    operation, cacheName, key, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Check if circuit breaker is open
     *
     * @return true if circuit is open (Redis unavailable)
     */
    public boolean isCircuitOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * Get cache manager instance (for direct access when needed)
     *
     * @return underlying cache manager
     */
    public CacheManager getCacheManager() {
        return cacheManager;
    }
}
