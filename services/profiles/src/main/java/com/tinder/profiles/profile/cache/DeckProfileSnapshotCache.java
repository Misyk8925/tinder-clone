package com.tinder.profiles.profile.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tinder.profiles.profile.dto.profileData.deck.DeckProfileDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class DeckProfileSnapshotCache {

    private static final String KEY_PREFIX = "profiles:deck-card:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ProfileCacheProperties properties;
    private final Cache<UUID, DeckProfileDto> localProfiles;

    public DeckProfileSnapshotCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            ProfileCacheProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.localProfiles = Caffeine.newBuilder()
                .maximumSize(properties.getDeckProfile().getMaxSize())
                .expireAfterWrite(properties.getDeckProfile().getTtl())
                .build();
    }

    public List<DeckProfileDto> getMany(
            List<UUID> requestedIds,
            Function<List<UUID>, List<DeckProfileDto>> databaseLoader
    ) {
        if (requestedIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, DeckProfileDto> found = new LinkedHashMap<>(requestedIds.size());
        List<UUID> localMisses = new ArrayList<>();

        for (UUID id : distinctIds(requestedIds)) {
            DeckProfileDto local = localProfiles.getIfPresent(id);
            if (local != null) {
                found.put(id, local);
            } else {
                localMisses.add(id);
            }
        }

        List<UUID> redisMisses = readRedis(localMisses, found);
        if (!redisMisses.isEmpty()) {
            List<DeckProfileDto> loaded = databaseLoader.apply(redisMisses);
            putAll(loaded);
            loaded.forEach(dto -> found.put(dto.id(), dto));
        }

        return requestedIds.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void putAll(Collection<DeckProfileDto> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }

        Map<String, String> redisValues = new LinkedHashMap<>(profiles.size());
        for (DeckProfileDto profile : profiles) {
            if (profile == null || profile.id() == null) {
                continue;
            }
            localProfiles.put(profile.id(), profile);
            try {
                redisValues.put(redisKey(profile.id()), objectMapper.writeValueAsString(profile));
            } catch (Exception e) {
                log.debug("Failed to serialize deck profile {}", profile.id(), e);
            }
        }

        try {
            redisValues.forEach((key, value) ->
                    redis.opsForValue().set(key, value, properties.getDeckProfile().getTtl()));
        } catch (Exception e) {
            log.debug("Failed to write deck profile snapshots to Redis", e);
        }
    }

    public void evict(UUID profileId) {
        if (profileId == null) {
            return;
        }

        localProfiles.invalidate(profileId);
        try {
            redis.delete(redisKey(profileId));
        } catch (Exception e) {
            log.debug("Failed to evict deck profile snapshot {}", profileId, e);
        }
    }

    public void evictAll(Collection<UUID> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return;
        }
        profileIds.forEach(this::evict);
    }

    private List<UUID> readRedis(List<UUID> ids, Map<UUID, DeckProfileDto> found) {
        if (ids.isEmpty()) {
            return List.of();
        }

        try {
            List<String> values = redis.opsForValue().multiGet(ids.stream().map(this::redisKey).toList());
            if (values == null) {
                return ids;
            }

            List<UUID> misses = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                UUID id = ids.get(i);
                String value = values.get(i);
                if (value == null || value.isBlank()) {
                    misses.add(id);
                    continue;
                }

                DeckProfileDto dto = objectMapper.readValue(value, DeckProfileDto.class);
                localProfiles.put(id, dto);
                found.put(id, dto);
            }
            return misses;
        } catch (Exception e) {
            log.debug("Failed to read deck profile snapshots from Redis", e);
            return ids;
        }
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String redisKey(UUID profileId) {
        return KEY_PREFIX + profileId;
    }
}
