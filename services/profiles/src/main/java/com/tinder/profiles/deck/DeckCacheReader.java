package com.tinder.profiles.deck;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeckCacheReader {

    private final StringRedisTemplate redis;

    public String deckKey(UUID id) {
        return "deck:" + id;
    }

    public List<UUID> readDeck(UUID viewerId, int offset, int limit) {

        String key = deckKey(viewerId);

        Set<String> deckUUIDs = redis.opsForZSet().reverseRange(key, offset, offset + limit - 1);

        if (deckUUIDs == null || deckUUIDs.isEmpty()) {
            return Collections.emptyList();
        }

        return deckUUIDs.stream().map(UUID::fromString).toList();
    }

    public boolean exists(UUID viewerId) {
        String key = deckKey(viewerId);
        if (redis.hasKey(key)) {
            Long size = redis.opsForZSet().size(key);
            return size != null && size > 0;
        }
        return false;
    }
}
