package com.tinder.deckread.readmodel;

import com.tinder.deckread.messaging.MatchCreatedEvent;
import com.tinder.deckread.messaging.SwipeSavedEvent;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.set.ReactiveSetCommands;
import io.quarkus.redis.datasource.sortedset.ReactiveSortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** First-swipe and match projections used for immediate output exclusion. */
@ApplicationScoped
public class ViewerMutationStore {

    public static final int REPEAT_RETENTION_DAYS = 7;

    private static final String APPLY_FIRST_SWIPE_SCRIPT = """
            local inserted = redis.call('HSETNX', KEYS[1], ARGV[1], ARGV[2])
            if inserted == 1 then
              redis.call('ZADD', KEYS[2], 'NX', ARGV[3], ARGV[1])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('EXPIRE', KEYS[2], ARGV[4])
            return inserted
            """;

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hashes;
    private final ReactiveSetCommands<String, String> sets;
    private final ReactiveSortedSetCommands<String, String> sortedSets;

    @Inject
    public ViewerMutationStore(@RedisClientName("read-model") ReactiveRedisDataSource redis) {
        this.redis = redis;
        this.hashes = redis.hash(String.class);
        this.sets = redis.set(String.class);
        this.sortedSets = redis.sortedSet(String.class);
    }

    public Uni<Void> applySwipe(SwipeSavedEvent event) {
        UUID viewerProfileId = UUID.fromString(event.profile1Id());
        UUID candidateProfileId = UUID.fromString(event.profile2Id());
        String storedFirstDecision = event.eventId() + "|" + event.timestamp() + "|" + event.decision();
        long retentionSeconds = Duration.ofDays(REPEAT_RETENTION_DAYS).toSeconds();
        return redis.execute(
                        "EVAL", APPLY_FIRST_SWIPE_SCRIPT, "2",
                        ReadModelKeys.swipes(viewerProfileId),
                        ReadModelKeys.repeatCandidates(viewerProfileId),
                        candidateProfileId.toString(), storedFirstDecision,
                        Long.toString(event.timestamp()), Long.toString(retentionSeconds))
                .replaceWithVoid();
    }

    public Uni<Void> applyMatch(MatchCreatedEvent event) {
        UUID left = UUID.fromString(event.profile1Id());
        UUID right = UUID.fromString(event.profile2Id());
        // The two writes live in different viewer slots. Both are idempotent;
        // at-least-once delivery repairs a partial cross-slot failure.
        return sets.sadd(ReadModelKeys.matched(left), right.toString())
                .flatMap(ignored -> sets.sadd(ReadModelKeys.matched(right), left.toString()))
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
