package com.tinder.profiles.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckCacheReaderTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DeckCacheReader reader;

    @BeforeEach
    void setUp() {
        reader = new DeckCacheReader(redis, new ObjectMapper());

        when(redis.opsForZSet()).thenReturn(zSetOperations);
        when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("readDeck should refill page when top raw members are invalid or deleted")
    void shouldRefillPageWhenEntriesAreFilteredOut() {
        UUID viewerId = UUID.randomUUID();
        UUID invalidated = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID valid1 = UUID.randomUUID();
        UUID valid2 = UUID.randomUUID();

        Set<String> batch = linkedSet(
                invalidated.toString(),
                deleted.toString(),
                valid1.toString(),
                valid2.toString()
        );

        when(zSetOperations.reverseRange(eq("deck:" + viewerId), eq(0L), eq(49L))).thenReturn(batch);
        when(valueOperations.get("deck:build:ts:" + viewerId)).thenReturn("1000");
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(
                List.of(false, true, false, false),
                Arrays.asList("2000", null, null, null)
        ));

        List<UUID> result = reader.readDeck(viewerId, 0, 2);

        assertThat(result).containsExactly(valid1, valid2);
    }

    @Test
    @DisplayName("readDeck should apply offset across valid entries, not raw positions")
    void shouldApplyOffsetAcrossValidEntries() {
        UUID viewerId = UUID.randomUUID();
        UUID invalidated = UUID.randomUUID();
        UUID valid1 = UUID.randomUUID();
        UUID valid2 = UUID.randomUUID();
        UUID valid3 = UUID.randomUUID();

        Set<String> batch = linkedSet(
                invalidated.toString(),
                valid1.toString(),
                valid2.toString(),
                valid3.toString()
        );

        when(zSetOperations.reverseRange(eq("deck:" + viewerId), eq(0L), eq(49L))).thenReturn(batch);
        when(valueOperations.get("deck:build:ts:" + viewerId)).thenReturn("1000");
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(List.of(
                List.of(false, false, false, false),
                Arrays.asList("2000", null, null, null)
        ));

        List<UUID> result = reader.readDeck(viewerId, 1, 2);

        assertThat(result).containsExactly(valid2, valid3);
    }

    @Test
    @DisplayName("readDeck should read next raw batch when filtered candidates cannot fill the page")
    void shouldReadNextRawBatchWhenFilteredCandidatesCannotFillPage() {
        UUID viewerId = UUID.randomUUID();
        List<UUID> invalidatedCandidates = randomIds(50);
        UUID valid1 = UUID.randomUUID();
        UUID valid2 = UUID.randomUUID();

        when(zSetOperations.reverseRange(eq("deck:" + viewerId), eq(0L), eq(49L)))
                .thenReturn(linkedSet(invalidatedCandidates));
        when(zSetOperations.reverseRange(eq("deck:" + viewerId), eq(50L), eq(99L)))
                .thenReturn(linkedSet(valid1.toString(), valid2.toString()));
        when(valueOperations.get("deck:build:ts:" + viewerId)).thenReturn("1000");
        when(redis.executePipelined(any(RedisCallback.class)))
                .thenReturn(List.of(
                        Collections.nCopies(50, false),
                        Collections.nCopies(50, "2000")
                ))
                .thenReturn(List.of(
                        List.of(false, false),
                        Arrays.asList(null, null)
                ));

        List<UUID> result = reader.readDeck(viewerId, 0, 2);

        assertThat(result).containsExactly(valid1, valid2);
    }

    private Set<String> linkedSet(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private Set<String> linkedSet(List<UUID> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.toString()));
        return result;
    }

    private List<UUID> randomIds(int count) {
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(UUID.randomUUID());
        }
        return ids;
    }
}
