package com.tinder.deckread.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.contracts.dto.DeckEntry;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
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
 * <p><b>Pure pass-through.</b> The reader returns exactly what is in the {@code deck:{viewerId}}
 * sorted set — no filtering of deleted / invalidated / swiped profiles, no build-timestamp logic.
 * Correctness is the write side's responsibility: the deck (write) service eagerly removes
 * deleted and critically-changed profiles from every affected deck (via its
 * {@code deck:contains:{profileId}} reverse index) and removes swiped profiles on each swipe
 * event. The only residue is a sub-millisecond window during a swipe's mark→remove sequence,
 * which is accepted.
 *
 * <p>Members are read highest-score-first (ZRANGE … REV) and parsed with Jackson into the shared
 * {@link DeckEntry}; a legacy bare-UUID member (a 36-char UUID string) is also tolerated.
 */
@ApplicationScoped
public class DeckRedisReader {

    private static final Logger LOG = Logger.getLogger(DeckRedisReader.class);

    private final ReactiveSortedSetCommands<String, String> sortedSet;
    private final ObjectMapper objectMapper;

    @Inject
    public DeckRedisReader(ReactiveRedisDataSource redis, ObjectMapper objectMapper) {
        this.sortedSet = redis.sortedSet(String.class);
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
        String key = DeckKeySchema.deck(viewerId);

        return sortedSet.zrange(key, off, end, new ZRangeArgs().rev())
                .map(members -> members.stream()
                        .map(this::parseProfileId)
                        .filter(Objects::nonNull)
                        .toList());
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
            return entry.profileId();
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
