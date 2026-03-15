package com.tinder.profiles.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeckCacheReader {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public String deckKey(UUID id) {
        return "deck:" + id;
    }

    public List<UUID> readDeck(UUID viewerId, int offset, int limit) {

        String key = deckKey(viewerId);

        Set<String> members = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1);

        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }

        return members.stream()
                .map(this::parseMember)
                .filter(entry -> !entry.isSwiped())
                .map(DeckEntryDto::profileId)
                .toList();
    }

    public boolean exists(UUID viewerId) {
        String key = deckKey(viewerId);
        if (redis.hasKey(key)) {
            Long size = redis.opsForZSet().size(key);
            return size != null && size > 0;
        }
        return false;
    }

    private DeckEntryDto parseMember(String member) {
        try {
            return objectMapper.readValue(member, DeckEntryDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse deck member as JSON, treating as plain UUID: {}", member);
            return new DeckEntryDto(UUID.fromString(member), false);
        }
    }

    record DeckEntryDto(UUID profileId, boolean isSwiped) {}
}
