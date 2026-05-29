package com.tinder.profiles.profile.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DeckPageCacheService {

    private static final String KEY_PREFIX = "profiles:deck-page:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ProfileCacheProperties properties;
    private final Cache<String, CachedDeckPage> localPages;

    public DeckPageCacheService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ProfileCacheProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.localPages = Caffeine.newBuilder()
                .maximumSize(properties.getDeckPage().getMaxSize())
                .expireAfterWrite(properties.getDeckPage().getTtl())
                .build();
    }

    public String get(UUID viewerId, int offset, int limit) {
        if (!properties.getDeckPage().isEnabled()) {
            return null;
        }

        String key = cacheKey(viewerId, offset, limit);
        CachedDeckPage local = localPages.getIfPresent(key);
        if (local != null) {
            return local.json();
        }

        try {
            String redisValue = redis.opsForValue().get(key);
            if (redisValue != null) {
                localPages.put(key, cacheValue(redisValue));
            }
            return redisValue;
        } catch (Exception e) {
            log.debug("Failed to read deck page cache {}", key, e);
            return null;
        }
    }

    public byte[] getBytes(UUID viewerId, int offset, int limit) {
        if (!properties.getDeckPage().isEnabled()) {
            return null;
        }

        String key = cacheKey(viewerId, offset, limit);
        CachedDeckPage local = localPages.getIfPresent(key);
        if (local != null) {
            return local.bytes();
        }

        try {
            String redisValue = redis.opsForValue().get(key);
            if (redisValue == null) {
                return null;
            }

            CachedDeckPage cached = cacheValue(redisValue);
            localPages.put(key, cached);
            return cached.bytes();
        } catch (Exception e) {
            log.debug("Failed to read deck page cache bytes {}", key, e);
            return null;
        }
    }

    public void put(UUID viewerId, int offset, int limit, Object profiles) {
        if (!properties.getDeckPage().isEnabled() || profiles == null) {
            return;
        }

        String key = cacheKey(viewerId, offset, limit);
        try {
            String json = objectMapper.writeValueAsString(profiles);
            localPages.put(key, cacheValue(json));
            redis.opsForValue().set(key, json, properties.getDeckPage().getTtl());
        } catch (Exception e) {
            log.debug("Failed to write deck page cache {}", key, e);
        }
    }

    public void evictViewer(UUID viewerId) {
        if (viewerId == null) {
            return;
        }

        String prefix = KEY_PREFIX + viewerId + ":";
        localPages.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        try {
            Set<String> keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Failed to evict deck page cache for viewer {}", viewerId, e);
        }
    }

    public void evictViewers(Collection<UUID> viewerIds) {
        if (viewerIds == null || viewerIds.isEmpty()) {
            return;
        }
        viewerIds.forEach(this::evictViewer);
    }

    private String cacheKey(UUID viewerId, int offset, int limit) {
        return KEY_PREFIX + viewerId + ":" + offset + ":" + limit;
    }

    private CachedDeckPage cacheValue(String json) {
        return new CachedDeckPage(json, json.getBytes(StandardCharsets.UTF_8));
    }

    private record CachedDeckPage(String json, byte[] bytes) {}
}
