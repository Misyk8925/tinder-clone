package com.tinder.profiles.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckCacheReader {

    private static final String RECENT_VIEWERS_KEY = "deck:recent:viewers";
    private static final String DECK_KEY_PREFIX = "deck:";
    private static final String DECK_BUILD_TIMESTAMP_KEY_PREFIX = "deck:build:ts:";
    private static final String DELETED_PROFILES_KEY = "deck:profile:deleted";
    private static final String PROFILE_INVALIDATED_AT_KEY_PREFIX = "deck:profile:invalidated-at:";
    private static final int READ_AHEAD_MULTIPLIER = 1;
    private static final int MIN_RAW_BATCH_SIZE = 1;
    private static final int MAX_RAW_BATCH_SIZE = 500;
    private static final long RECENT_VIEWER_TOUCH_INTERVAL_MS = 60_000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, Long> recentViewerTouches = new ConcurrentHashMap<>();

    public String deckKey(UUID id) {
        return DECK_KEY_PREFIX + id;
    }

    public void markViewerActive(UUID viewerId) {
        long now = System.currentTimeMillis();
        Long lastTouchedAt = recentViewerTouches.get(viewerId);
        if (lastTouchedAt != null && now - lastTouchedAt < RECENT_VIEWER_TOUCH_INTERVAL_MS) {
            return;
        }
        recentViewerTouches.put(viewerId, now);
        redis.opsForZSet().add(RECENT_VIEWERS_KEY, viewerId.toString(), now);
    }

    public List<UUID> readDeck(UUID viewerId, int offset, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        long deckBuildTimestamp = readDeckBuildTimestampMillis(viewerId);
        int rawBatchSize = rawBatchSizeFor(limit);
        int nextRawOffset = 0;
        int validCandidatesToSkip = offset;
        List<UUID> page = new ArrayList<>(limit);

        while (page.size() < limit) {
            List<DeckEntryDto> rawBatch = readRawDeckBatch(viewerId, nextRawOffset, rawBatchSize);
            if (rawBatch.isEmpty()) {
                break;
            }

            List<DeckEntryDto> validBatch = keepCurrentlyValidCandidates(rawBatch, deckBuildTimestamp);
            validCandidatesToSkip = appendPageCandidates(page, validBatch, validCandidatesToSkip, limit);

            if (rawBatch.size() < rawBatchSize) {
                break;
            }
            nextRawOffset += rawBatchSize;
        }

        return page;
    }

    public boolean exists(UUID viewerId) {
        String key = deckKey(viewerId);
        if (redis.hasKey(key)) {
            Long size = redis.opsForZSet().size(key);
            return size != null && size > 0;
        }
        return false;
    }

    public List<UUID> getRecentViewerIds(Duration window, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        double cutoff = System.currentTimeMillis() - window.toMillis();
        Set<String> viewerIds = redis.opsForZSet()
                .reverseRangeByScore(RECENT_VIEWERS_KEY, cutoff, Double.MAX_VALUE, 0, limit);
        if (viewerIds == null || viewerIds.isEmpty()) {
            return List.of();
        }

        return viewerIds.stream()
                .map(this::parseUuid)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<DeckEntryDto> readRawDeckBatch(UUID viewerId, int rawOffset, int batchSize) {
        Set<String> members = redis.opsForZSet()
                .reverseRange(deckKey(viewerId), rawOffset, rawOffset + batchSize - 1);

        if (members == null || members.isEmpty()) {
            return List.of();
        }

        return members.stream()
                .map(this::parseMember)
                .toList();
    }

    private int appendPageCandidates(List<UUID> page, List<DeckEntryDto> validBatch, int candidatesToSkip, int limit) {
        int remainingCandidatesToSkip = candidatesToSkip;

        for (DeckEntryDto entry : validBatch) {
            if (remainingCandidatesToSkip > 0) {
                remainingCandidatesToSkip--;
                continue;
            }

            page.add(entry.profileId());
            if (page.size() == limit) {
                break;
            }
        }

        return remainingCandidatesToSkip;
    }

    private int rawBatchSizeFor(int limit) {
        return Math.min(Math.max(limit * READ_AHEAD_MULTIPLIER, MIN_RAW_BATCH_SIZE), MAX_RAW_BATCH_SIZE);
    }

    private List<DeckEntryDto> keepCurrentlyValidCandidates(List<DeckEntryDto> rawCandidates, long deckBuildTimestamp) {
        List<DeckEntryDto> candidates = rawCandidates.stream()
                .filter(entry -> !entry.isSwiped())
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        EntryMetadata metadata = loadEntryMetadata(candidates);

        return candidates.stream()
                .filter(entry -> !metadata.deletedIds().contains(entry.profileId()))
                .filter(entry -> metadata.invalidatedAt().getOrDefault(entry.profileId(), Long.MIN_VALUE) <= deckBuildTimestamp)
                .toList();
    }

    private long readDeckBuildTimestampMillis(UUID viewerId) {
        String value = redis.opsForValue().get(deckBuildTimestampKey(viewerId));
        if (value == null || value.isBlank()) {
            return Long.MIN_VALUE;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid deck build timestamp for viewer {}: {}", viewerId, value);
            return Long.MIN_VALUE;
        }
    }

    private EntryMetadata loadEntryMetadata(List<DeckEntryDto> entries) {
        RedisSerializer<String> serializer = redis.getStringSerializer();
        List<String> profileIds = entries.stream()
                .map(entry -> entry.profileId().toString())
                .toList();
        List<String> invalidationKeys = entries.stream()
                .map(entry -> profileInvalidatedAtKey(entry.profileId()))
                .toList();

        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            connection.setCommands().sMIsMember(
                    serializer.serialize(DELETED_PROFILES_KEY),
                    serializeAll(serializer, profileIds)
            );
            connection.stringCommands().mGet(serializeAll(serializer, invalidationKeys));
            return null;
        });

        return new EntryMetadata(
                loadDeletedIds(entries, pipelineResult(results, 0)),
                loadInvalidationTimes(entries, pipelineResult(results, 1))
        );
    }

    private Set<UUID> loadDeletedIds(List<DeckEntryDto> entries, Object pipelineResult) {
        Set<UUID> deleted = new HashSet<>();
        if (!(pipelineResult instanceof List<?> deletedMembership)) {
            return deleted;
        }

        for (int i = 0; i < entries.size() && i < deletedMembership.size(); i++) {
            if (Boolean.TRUE.equals(deletedMembership.get(i))) {
                DeckEntryDto entry = entries.get(i);
                deleted.add(entry.profileId());
            }
        }

        return deleted;
    }

    private Map<UUID, Long> loadInvalidationTimes(List<DeckEntryDto> entries, Object pipelineResult) {
        Map<UUID, Long> invalidatedAt = new HashMap<>();
        if (!(pipelineResult instanceof List<?> values)) {
            return invalidatedAt;
        }

        for (int i = 0; i < entries.size() && i < values.size(); i++) {
            String value = deserializePipelineString(values.get(i));
            if (value == null || value.isBlank()) {
                continue;
            }

            UUID profileId = entries.get(i).profileId();
            try {
                invalidatedAt.put(profileId, Long.parseLong(value));
            } catch (NumberFormatException e) {
                log.warn("Invalid profile invalidation timestamp for profile {}: {}", profileId, value);
                invalidatedAt.put(profileId, Long.MAX_VALUE);
            }
        }

        return invalidatedAt;
    }

    private Object pipelineResult(List<Object> results, int index) {
        if (results == null || results.size() <= index) {
            return null;
        }
        return results.get(index);
    }

    private byte[][] serializeAll(RedisSerializer<String> serializer, List<String> values) {
        return values.stream()
                .map(serializer::serialize)
                .toArray(byte[][]::new);
    }

    private String deserializePipelineString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof byte[] bytes) {
            return redis.getStringSerializer().deserialize(bytes);
        }
        return value.toString();
    }

    private String deckBuildTimestampKey(UUID viewerId) {
        return DECK_BUILD_TIMESTAMP_KEY_PREFIX + viewerId;
    }

    private String profileInvalidatedAtKey(UUID profileId) {
        return PROFILE_INVALIDATED_AT_KEY_PREFIX + profileId;
    }

    private DeckEntryDto parseMember(String member) {
        DeckEntryDto fastParsed = parseJsonMemberFast(member);
        if (fastParsed != null) {
            return fastParsed;
        }

        try {
            return objectMapper.readValue(member, DeckEntryDto.class);
        } catch (Exception jsonError) {
            return parseLegacyUuidMember(member, jsonError);
        }
    }

    private DeckEntryDto parseJsonMemberFast(String member) {
        if (member == null || member.isBlank()) {
            return null;
        }
        if (member.length() == 36 && member.charAt(8) == '-') {
            return new DeckEntryDto(UUID.fromString(member), false);
        }

        int profileIdKey = member.indexOf("\"profileId\"");
        if (profileIdKey < 0) {
            return null;
        }
        int colon = member.indexOf(':', profileIdKey);
        int startQuote = colon >= 0 ? member.indexOf('"', colon) : -1;
        int endQuote = startQuote >= 0 ? member.indexOf('"', startQuote + 1) : -1;
        if (endQuote < 0) {
            return null;
        }

        int swipedKey = member.indexOf("\"isSwiped\"");
        boolean swiped = false;
        if (swipedKey >= 0) {
            int swipedColon = member.indexOf(':', swipedKey);
            swiped = swipedColon >= 0 && member.regionMatches(swipedColon + 1, "true", 0, 4);
        }

        return new DeckEntryDto(UUID.fromString(member.substring(startQuote + 1, endQuote)), swiped);
    }

    private DeckEntryDto parseLegacyUuidMember(String member, Exception jsonError) {
        try {
            return new DeckEntryDto(UUID.fromString(member), false);
        } catch (IllegalArgumentException uuidError) {
            log.warn("Failed to parse deck member as JSON or UUID: {}", member, jsonError);
            throw uuidError;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            log.debug("Skipping malformed recent viewer id {}", value);
            return null;
        }
    }

    record DeckEntryDto(UUID profileId, boolean isSwiped) {}

    private record EntryMetadata(Set<UUID> deletedIds, Map<UUID, Long> invalidatedAt) {}
}
