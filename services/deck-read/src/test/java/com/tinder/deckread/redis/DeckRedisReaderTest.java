package com.tinder.deckread.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.deck.DeckRedisKeys;
import com.tinder.contracts.dto.DeckEntry;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reader tests against a real Redis provided by Quarkus Dev Services (throwaway container).
 *
 * <p>The reader asserts ordering, paging, member parsing and the source contract that
 * entries already marked as swiped must not be imported as fresh candidates.
 * Each test uses a fresh viewerId so cases don't interfere.
 */
@QuarkusTest
class DeckRedisReaderTest {

    @Inject
    DeckRedisReader reader;

    @Inject
    @RedisClientName("deck-source")
    RedisDataSource redis;

    private final ObjectMapper mapper = new ObjectMapper();

    private SortedSetCommands<String, String> zset() {
        return redis.sortedSet(String.class, String.class);
    }

    private ValueCommands<String, String> values() {
        return redis.value(String.class);
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
        String key = DeckRedisKeys.deck(viewer);

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
        String key = DeckRedisKeys.deck(viewer);

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
        zset().zadd(DeckRedisKeys.deck(viewer), 5.0, member(profile, false));

        assertThat(read(viewer, 0, 10)).containsExactly(profile);
    }

    @Test
    @Tag("acceptance")
    @DisplayName("Scenario: Given a source entry already marked as swiped, when source ordering is imported, then it cannot enter fresh candidates")
    void excludesSourceEntriesAlreadyMarkedAsSwiped() {
        // Given
        UUID viewer = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        UUID alreadySwiped = UUID.randomUUID();
        String key = DeckRedisKeys.deck(viewer);
        zset().zadd(key, 20.0, member(alreadySwiped, true));
        zset().zadd(key, 10.0, member(fresh, false));

        // When
        List<UUID> imported = read(viewer, 0, 10);

        // Then
        assertThat(imported).containsExactly(fresh);
    }

    @Test
    void parsesLegacyBareUuidMember() {
        UUID viewer = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        // Legacy member: the raw UUID string, not JSON.
        zset().zadd(DeckRedisKeys.deck(viewer), 5.0, profile.toString());

        assertThat(read(viewer, 0, 10)).containsExactly(profile);
    }

    @Test
    void missingDeckReturnsEmpty() {
        assertThat(read(UUID.randomUUID(), 0, 10)).isEmpty();
    }

    @Test
    void nonPositiveLimitReturnsEmpty() {
        UUID viewer = UUID.randomUUID();
        zset().zadd(DeckRedisKeys.deck(viewer), 5.0, member(UUID.randomUUID(), false));

        assertThat(read(viewer, 0, 0)).isEmpty();
        assertThat(read(viewer, 0, -3)).isEmpty();
    }

    @Test
    void stableReadRequiresAnExplicitSourceBuildTimestamp() {
        UUID viewer = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        zset().zadd(DeckRedisKeys.deck(viewer), 5.0, member(candidate, false));

        assertThatThrownBy(() -> reader.readStable(viewer, 10).await().indefinitely())
                .hasMessage("Deck source has no stable build timestamp after three import attempts");
    }

    @Test
    void stableReadReturnsIdsWhenTheNonNullTimestampDoesNotChange() {
        UUID viewer = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        zset().zadd(DeckRedisKeys.deck(viewer), 5.0, member(candidate, false));
        values().set(DeckRedisKeys.buildTimestamp(viewer), "source-generation-42");

        SourceDeckSnapshot snapshot = reader.readStable(viewer, 10).await().indefinitely();

        assertThat(snapshot.orderedProfileIds()).containsExactly(candidate);
        assertThat(snapshot.buildTimestamp()).isEqualTo("source-generation-42");
    }
}
