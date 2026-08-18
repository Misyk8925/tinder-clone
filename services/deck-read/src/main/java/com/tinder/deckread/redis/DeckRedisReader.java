package com.tinder.deckread.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.deck.DeckRedisKeys;
import com.tinder.contracts.dto.DeckEntry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads a page of the cached deck for a viewer and returns the ordered profile IDs.
 *
 * <p>The source client is read-only. Filtering and card hydration happen later
 * from Deck Read's own event projections; this class only imports ordered IDs
 * and verifies a stable build timestamp.
 *
 * <p>Members are read highest-score-first (ZRANGE … REV) and parsed with Jackson into the shared
 * {@link DeckEntry}; a legacy bare-UUID member (a 36-char UUID string) is also tolerated.
 */
@ApplicationScoped
public class DeckRedisReader {

    private static final Logger LOG = Logger.getLogger(DeckRedisReader.class);

    private final ReactiveSortedSetCommands<String, String> sortedSet;
    private final ReactiveValueCommands<String, String> values;
    private final ObjectMapper objectMapper;

    @Inject
    public DeckRedisReader(@RedisClientName("deck-source") ReactiveRedisDataSource redis,
                           ObjectMapper objectMapper) {
        this.sortedSet = redis.sortedSet(String.class);
        this.values = redis.value(String.class);
        this.objectMapper = objectMapper;
    }

    /**
     * @param viewerId the viewer whose deck to read
     * @param offset   0-based offset into the deck (highest score first)
     * @param limit    maximum number of entries to return
     * @return ordered profile IDs; empty if the deck is missing or {@code limit <= 0}
     */
    public Uni<List<UUID>> read(UUID viewerId, int offset, int limit) {
        if (limit <= 0) {
            return Uni.createFrom().item(List.of());
        }
        int off = Math.max(offset, 0);
        long end = (long) off + limit - 1;
        String key = DeckRedisKeys.deck(viewerId);

        return sortedSet.zrange(key, off, end, new ZRangeArgs().rev())
                .map(members -> members.stream()
                        .map(this::parseProfileId)
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * Imports a whole source snapshot only when the Deck build timestamp is
     * unchanged before and after the ZSET read. A concurrent writer causes a
     * bounded retry instead of a mixed generation.
     */
    public Uni<SourceDeckSnapshot> readStable(UUID viewerProfileId, int limit) {
        return readStable(viewerProfileId, Math.min(Math.max(limit, 0), 500), 0);
    }

    private Uni<SourceDeckSnapshot> readStable(UUID viewerProfileId, int limit, int attempt) {
        String timestampKey = DeckRedisKeys.buildTimestamp(viewerProfileId);
        return values.get(timestampKey)
                .flatMap(before -> read(viewerProfileId, 0, limit)
                        .flatMap(ids -> values.get(timestampKey)
                                .flatMap(after -> {
                                    if (before != null && Objects.equals(before, after)) {
                                        return Uni.createFrom().item(new SourceDeckSnapshot(ids, after));
                                    }
                                    if (attempt >= 2) {
                                        if (before == null || after == null) {
                                            return Uni.createFrom().failure(new IllegalStateException(
                                                    "Deck source has no stable build timestamp after three import attempts"));
                                        }
                                        return Uni.createFrom().failure(new IllegalStateException(
                                                "Deck source changed during three import attempts"));
                                    }
                                    return readStable(viewerProfileId, limit, attempt + 1);
                                })));
    }

    /** Parse a sorted-set member into its profileId. Returns null on unrecoverable garbage. */
    private UUID parseProfileId(String member) {
        if (member == null || member.isBlank()) {
            return null;
        }
        // Legacy format: the member is a bare UUID string.
        if (member.length() == 36 && member.charAt(8) == '-') {
            return tryUuid(member);
        }
        try {
            DeckEntry entry = objectMapper.readValue(member, DeckEntry.class);
            return entry.isSwiped() ? null : entry.profileId();
        } catch (Exception jsonError) {
            UUID legacy = tryUuid(member);
            if (legacy == null) {
                LOG.warnf("Skipping unparseable deck member: %s", member);
            }
            return legacy;
        }
    }

    private static UUID tryUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
