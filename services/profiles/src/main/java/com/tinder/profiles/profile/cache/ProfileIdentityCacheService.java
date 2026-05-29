package com.tinder.profiles.profile.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class ProfileIdentityCacheService {

    private static final String KEY_PREFIX = "profiles:jwt-sub:";

    private final StringRedisTemplate redis;
    private final ProfileCacheProperties properties;
    private final Cache<String, UUID> localProfileIds;

    public ProfileIdentityCacheService(StringRedisTemplate redis, ProfileCacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.localProfileIds = Caffeine.newBuilder()
                .maximumSize(properties.getJwtProfileId().getMaxSize())
                .expireAfterWrite(properties.getJwtProfileId().getTtl())
                .build();
    }

    public UUID getProfileId(String userId, Function<String, UUID> loader) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        UUID local = localProfileIds.getIfPresent(userId);
        if (local != null) {
            return local;
        }

        UUID redisValue = readRedis(userId);
        if (redisValue != null) {
            localProfileIds.put(userId, redisValue);
            return redisValue;
        }

        UUID loaded = loader.apply(userId);
        if (loaded != null) {
            put(userId, loaded);
        }
        return loaded;
    }

    public UUID getCachedProfileId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        UUID local = localProfileIds.getIfPresent(userId);
        if (local != null) {
            return local;
        }

        UUID redisValue = readRedis(userId);
        if (redisValue != null) {
            localProfileIds.put(userId, redisValue);
        }
        return redisValue;
    }

    public void put(String userId, UUID profileId) {
        if (userId == null || userId.isBlank() || profileId == null) {
            return;
        }

        localProfileIds.put(userId, profileId);
        try {
            redis.opsForValue().set(redisKey(userId), profileId.toString(), properties.getJwtProfileId().getTtl());
        } catch (Exception e) {
            log.debug("Failed to cache profile id for user {}", userId, e);
        }
    }

    public void evict(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        localProfileIds.invalidate(userId);
        try {
            redis.delete(redisKey(userId));
        } catch (Exception e) {
            log.debug("Failed to evict cached profile id for user {}", userId, e);
        }
    }

    private UUID readRedis(String userId) {
        try {
            String value = redis.opsForValue().get(redisKey(userId));
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (Exception e) {
            log.debug("Failed to read cached profile id for user {}", userId, e);
            return null;
        }
    }

    private String redisKey(String userId) {
        return KEY_PREFIX + userId;
    }
}
