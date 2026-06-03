package com.tinder.deckread.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.dto.DeckEntry;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reader tests against a real Redis provided by Quarkus Dev Services (throwaway container).
 *
 * <p>The reader is a pure pass-through, so these assert ordering, paging and member parsing only.
 * Deleted/invalidated/swiped filtering is the write side's responsibility and is NOT tested here.
 * Each test uses a fresh viewerId so cases don't interfere.
 */
@QuarkusTest
class DeckRedisReaderTest {

    @Inject
    DeckRedisReader reader;

    @Inject
    RedisDataSource redis;

    private final ObjectMapper mapper = new ObjectMapper();

    private SortedSetCommands<String, String> zset() {
        return redis.sortedSet(String.class, String.class);
    }

    private String member(UUID profileId, boolean swiped) {
        try {
            // Serialize exactly as the write service does, to prove byte-compatibility.
            return mapper.writeValueAsString(new DeckEntry(profileId, swiped));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<UUID> read(UUID viewerId, int offset, int limit) {
        return reader.read(viewerId, offset, limit).await().indefinitely();
    }

    @Test
    void returnsHighestScoreFirst() {
        UUID viewer = UUID.randomUUID();
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        UUID mid = UUID.randomUUID();
        String key = DeckKeySchema.deck(viewer);

        zset().zadd(key, 10.0, member(low, false));
        zset().zadd(key, 30.0, member(high, false));
        zset().zadd(key, 20.0, member(mid, false));

        assertThat(read(viewer, 0, 10)).containsExactly(high, mid, low);
    }

    @Test
    void appliesOffsetAndLimit() {
        UUID viewer = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        String key = DeckKeySchema.deck(viewer);

        zset().zadd(key, 30.0, member(a, false)); // rank 0
        zset().zadd(key, 20.0, member(b, false)); // rank 1
        zset().zadd(key, 10.0, member(c, false)); // rank 2

        assertThat(read(viewer, 1, 1)).containsExactly(b);
        assertThat(read(viewer, 0, 2)).containsExactly(a, b);
    }

    @Test
    void parsesJacksonMember() {
        UUID viewer = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        zset().zadd(DeckKeySchema.deck(viewer), 5.0, member(profile, false));

        assertThat(read(viewer, 0, 10)).containsExactly(profile);
    }

    @Test
    void parsesLegacyBareUuidMember() {
        UUID viewer = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        // Legacy member: the raw UUID string, not JSON.
        zset().zadd(DeckKeySchema.deck(viewer), 5.0, profile.toString());

        assertThat(read(viewer, 0, 10)).containsExactly(profile);
    }

    @Test
    void missingDeckReturnsEmpty() {
        assertThat(read(UUID.randomUUID(), 0, 10)).isEmpty();
    }

    @Test
    void nonPositiveLimitReturnsEmpty() {
        UUID viewer = UUID.randomUUID();
        zset().zadd(DeckKeySchema.deck(viewer), 5.0, member(UUID.randomUUID(), false));

        assertThat(read(viewer, 0, 0)).isEmpty();
        assertThat(read(viewer, 0, -3)).isEmpty();
    }
}
