package com.tinder.deckread.readmodel;

import com.tinder.contracts.event.v1.SwipeRevokedEventV1;
import com.tinder.deckread.messaging.MatchCreatedEvent;
import com.tinder.deckread.messaging.SwipeSavedEvent;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** First-swipe, rewind, and match projections used for immediate output exclusion/ordering. */
@ApplicationScoped
public class ViewerMutationStore {

    public static final int REPEAT_RETENTION_DAYS = 7;
    private static final long REWOUND_TTL_SECONDS = Duration.ofHours(24).toSeconds();

    private static final String APPLY_FIRST_SWIPE_SCRIPT = """
            local rewound = redis.call('GET', KEYS[3])
            local clearRewound = false
            if rewound then
              local sep = string.find(rewound, '|', 1, true)
              if sep then
                local rewoundId = string.sub(rewound, 1, sep - 1)
                local rewoundAt = tonumber(string.sub(rewound, sep + 1)) or 0
                local swipeTs = tonumber(ARGV[3]) or 0
                if rewoundId == ARGV[1] then
                  if swipeTs < rewoundAt then
                    return 0
                  end
                  clearRewound = true
                end
              end
            end
            local inserted = redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2])
            if inserted == 1 then
              redis.call('ZADD', KEYS[2], 'NX', ARGV[3], ARGV[1])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[4])
            if clearRewound then
              redis.call('DEL', KEYS[3])
            end
            return inserted
            """;

    private static final String APPLY_SWIPE_REVOKED_SCRIPT = """
            if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 1 then
              return 0
            end
            local existing = redis.call('HGET', KEYS[1], ARGV[1])
            if existing then
              local firstSep = string.find(existing, '|', 1, true)
              if firstSep then
                local secondSep = string.find(existing, '|', firstSep + 1, true)
                if secondSep then
                  local swipeTs = tonumber(string.sub(existing, firstSep + 1, secondSep - 1)) or 0
                  if swipeTs > (tonumber(ARGV[2]) or 0) then
                    return 0
                  end
                end
              end
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('SET', KEYS[4], ARGV[1] .. '|' .. ARGV[2], 'EX', ARGV[3])
            return 1
            """;

    private static final String CLEAR_REWOUND_IF_MATCHED_SCRIPT = """
            local rewound = redis.call('GET', KEYS[1])
            if rewound then
              local sep = string.find(rewound, '|', 1, true)
              if sep and string.sub(rewound, 1, sep - 1) == ARGV[1] then
                redis.call('DEL', KEYS[1])
              end
            end
            return 1
            """;

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hashes;
    private final ReactiveSetCommands<String, String> sets;
    private final ReactiveSortedSetCommands<String, String> sortedSets;
    private final ReactiveValueCommands<String, String> values;

    @Inject
    public ViewerMutationStore(@RedisClientName("read-model") ReactiveRedisDataSource redis) {
        this.redis = redis;
        this.hashes = redis.hash(String.class);
        this.sets = redis.set(String.class);
        this.sortedSets = redis.sortedSet(String.class);
        this.values = redis.value(String.class);
    }

    public Uni<Void> applySwipe(SwipeSavedEvent event) {
        UUID viewerProfileId = UUID.fromString(event.profile1Id());
        UUID candidateProfileId = UUID.fromString(event.profile2Id());
        String storedFirstDecision = event.eventId() + "|" + event.timestamp() + "|" + event.decision();
        long retentionSeconds = Duration.ofDays(REPEAT_RETENTION_DAYS).toSeconds();
        return redis.execute(
                        "EVAL", APPLY_FIRST_SWIPE_SCRIPT, "3",
                        ReadModelKeys.swipes(viewerProfileId),
                        ReadModelKeys.repeatCandidates(viewerProfileId),
                        ReadModelKeys.rewound(viewerProfileId),
                        candidateProfileId.toString(), storedFirstDecision,
                        Long.toString(event.timestamp()), Long.toString(retentionSeconds))
                .replaceWithVoid();
    }

    public Uni<Void> applySwipeRevoked(SwipeRevokedEventV1 event) {
        UUID viewerProfileId = UUID.fromString(event.profile1Id());
        UUID candidateProfileId = UUID.fromString(event.profile2Id());
        return redis.execute(
                        "EVAL", APPLY_SWIPE_REVOKED_SCRIPT, "4",
                        ReadModelKeys.swipes(viewerProfileId),
                        ReadModelKeys.repeatCandidates(viewerProfileId),
                        ReadModelKeys.matched(viewerProfileId),
                        ReadModelKeys.rewound(viewerProfileId),
                        candidateProfileId.toString(),
                        Long.toString(event.revokedAt()),
                        Long.toString(REWOUND_TTL_SECONDS))
                .replaceWithVoid();
    }

    public Uni<Optional<UUID>> rewound(UUID viewerProfileId) {
        return values.get(ReadModelKeys.rewound(viewerProfileId))
                .map(raw -> {
                    if (raw == null || raw.isBlank()) {
                        return Optional.<UUID>empty();
                    }
                    int sep = raw.indexOf('|');
                    String idPart = sep > 0 ? raw.substring(0, sep) : raw;
                    try {
                        return Optional.of(UUID.fromString(idPart));
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                });
    }

    public Uni<Void> applyMatch(MatchCreatedEvent event) {
        UUID left = UUID.fromString(event.profile1Id());
        UUID right = UUID.fromString(event.profile2Id());
        long retentionSeconds = Duration.ofDays(REPEAT_RETENTION_DAYS).toSeconds();
        // The two writes live in different viewer slots. Both are idempotent;
        // at-least-once delivery repairs a partial cross-slot failure.
        return sets.sadd(ReadModelKeys.matched(left), right.toString())
                .flatMap(ignored -> redis.execute(
                        "EXPIRE", ReadModelKeys.matched(left), Long.toString(retentionSeconds)))
                .flatMap(ignored -> redis.execute(
                        "EVAL", CLEAR_REWOUND_IF_MATCHED_SCRIPT, "1",
                        ReadModelKeys.rewound(left), right.toString()))
                .flatMap(ignored -> sets.sadd(ReadModelKeys.matched(right), left.toString()))
                .flatMap(ignored -> redis.execute(
                        "EXPIRE", ReadModelKeys.matched(right), Long.toString(retentionSeconds)))
                .flatMap(ignored -> redis.execute(
                        "EVAL", CLEAR_REWOUND_IF_MATCHED_SCRIPT, "1",
                        ReadModelKeys.rewound(right), left.toString()))
                .replaceWithVoid();
    }

    public Uni<Void> suppress(UUID viewerProfileId, UUID candidateProfileId) {
        return sets.sadd(ReadModelKeys.suppressed(viewerProfileId), candidateProfileId.toString())
                .flatMap(ignored -> redis.execute(
                        "EXPIRE", ReadModelKeys.suppressed(viewerProfileId),
                        Long.toString(Duration.ofDays(REPEAT_RETENTION_DAYS).toSeconds())))
                .replaceWithVoid();
    }

    public Uni<Set<UUID>> swiped(UUID viewerProfileId, Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return Uni.createFrom().item(Set.of());
        }
        String[] fields = candidates.stream().map(UUID::toString).toArray(String[]::new);
        return hashes.hmget(ReadModelKeys.swipes(viewerProfileId), fields)
                .map(found -> {
                    Set<UUID> isSwiped = new HashSet<>();
                    for (int index = 0; index < fields.length; index++) {
                        if (found.get(fields[index]) != null) {
                            isSwiped.add(UUID.fromString(fields[index]));
                        }
                    }
                    return Set.copyOf(isSwiped);
                });
    }

    public Uni<Set<UUID>> matched(UUID viewerProfileId, Collection<UUID> candidates) {
        if (candidates.isEmpty()) {
            return Uni.createFrom().item(Set.of());
        }
        String[] members = candidates.stream().map(UUID::toString).toArray(String[]::new);
        return sets.smismember(ReadModelKeys.matched(viewerProfileId), members)
                .map(matches -> {
                    Set<UUID> matched = new HashSet<>();
                    for (int index = 0; index < members.length; index++) {
                        if (Boolean.TRUE.equals(matches.get(index))) {
                            matched.add(UUID.fromString(members[index]));
                        }
                    }
                    return Set.copyOf(matched);
                });
    }

    public Uni<List<UUID>> repeatCandidates(UUID viewerProfileId, int limit, Instant now) {
        long cutoff = now.minus(Duration.ofDays(REPEAT_RETENTION_DAYS)).toEpochMilli();
        return sortedSets.zrangeWithScores(
                        ReadModelKeys.repeatCandidates(viewerProfileId),
                        0, Math.max(0, limit - 1), new ZRangeArgs().rev())
                .map(values -> values.stream()
                        .filter(value -> value.score() >= cutoff)
                        .map(value -> UUID.fromString(value.value()))
                        .toList());
    }
}
