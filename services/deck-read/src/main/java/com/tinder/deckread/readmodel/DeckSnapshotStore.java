package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckState;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.list.ReactiveListCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Viewer snapshot persistence; all install keys share the viewer hash tag. */
@ApplicationScoped
public class DeckSnapshotStore {

    public static final int MAX_FRESH = 500;
    public static final int MAX_REPEAT = 500;
    public static final int SOFT_FRESHNESS_MINUTES = 60;
    public static final int SNAPSHOT_RETENTION_DAYS = 7;
    private static final int OLD_GENERATION_RETENTION_SECONDS = 30 * 60;

    private static final String INSTALL_SCRIPT = """
            if ARGV[3] ~= '' and redis.call('GET', KEYS[6]) ~= ARGV[3] then
              return -1
            end
            local currentGeneration = tonumber(redis.call('HGET', KEYS[1], 'generation') or '0')
            if currentGeneration ~= tonumber(ARGV[1]) then
              return -2
            end
            redis.call('DEL', KEYS[2], KEYS[3])
            local index = 9
            local freshCount = tonumber(ARGV[8])
            for i = 1, freshCount do
              redis.call('RPUSH', KEYS[2], ARGV[index])
              index = index + 1
            end
            local repeatCount = tonumber(ARGV[index])
            index = index + 1
            for i = 1, repeatCount do
              redis.call('RPUSH', KEYS[3], ARGV[index])
              index = index + 1
            end
            redis.call('HSET', KEYS[1],
              'generation', ARGV[2],
              'builtAt', ARGV[4],
              'state', ARGV[5],
              'sourceBuildTimestamp', ARGV[6],
              'failureCount', '0',
              'unavailable', 'false')
            redis.call('HDEL', KEYS[1], 'refreshStartedAt', 'lastFailureAt')
            redis.call('EXPIRE', KEYS[1], ARGV[7])
            if freshCount > 0 then redis.call('EXPIRE', KEYS[2], ARGV[7]) end
            if repeatCount > 0 then redis.call('EXPIRE', KEYS[3], ARGV[7]) end
            if ARGV[index] == 'true' then
              redis.call('EXPIRE', KEYS[4], ARGV[index + 1])
              redis.call('EXPIRE', KEYS[5], ARGV[index + 1])
            end
            return tonumber(ARGV[2])
            """;

    private static final String MARK_UNAVAILABLE_SCRIPT = """
            if ARGV[1] ~= '' and redis.call('GET', KEYS[2]) ~= ARGV[1] then
              return -1
            end
            local currentGeneration = tonumber(redis.call('HGET', KEYS[1], 'generation') or '0')
            local expectedGeneration = tonumber(ARGV[2])
            if expectedGeneration >= 0 and currentGeneration ~= expectedGeneration then
              return -2
            end
            if currentGeneration == 0 then
              currentGeneration = 1
              redis.call('HSET', KEYS[1],
                'generation', currentGeneration,
                'builtAt', ARGV[3],
                'sourceBuildTimestamp', '')
            end
            redis.call('HSET', KEYS[1], 'state', 'DEGRADED', 'unavailable', 'true')
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return currentGeneration
            """;

    private static final String RECORD_FAILURE_SCRIPT = """
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
              return -1
            end
            local currentGeneration = tonumber(redis.call('HGET', KEYS[1], 'generation') or '0')
            if currentGeneration ~= tonumber(ARGV[2]) then
              return -2
            end
            local failureCount = redis.call('HINCRBY', KEYS[1], 'failureCount', 1)
            redis.call('HSET', KEYS[1], 'lastFailureAt', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return failureCount
            """;

    private static final String MARK_REFRESH_REQUESTED_SCRIPT = """
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
              return -1
            end
            local currentGeneration = tonumber(redis.call('HGET', KEYS[1], 'generation') or '0')
            if currentGeneration ~= tonumber(ARGV[2]) then
              return -2
            end
            redis.call('HSETNX', KEYS[1], 'refreshStartedAt', ARGV[3])
            redis.call('HSET', KEYS[1], 'state', 'REFRESHING')
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """;

    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private static final String RENEW_LOCK_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """;

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hashes;
    private final ReactiveListCommands<String, String> lists;

    @Inject
    public DeckSnapshotStore(@RedisClientName("read-model") ReactiveRedisDataSource redis) {
        this.redis = redis;
        this.hashes = redis.hash(String.class);
        this.lists = redis.list(String.class);
    }

    public Uni<Optional<DeckSnapshot>> load(UUID viewerProfileId) {
        return hashes.hgetall(ReadModelKeys.viewerMeta(viewerProfileId))
                .flatMap(fields -> {
                    if (fields == null || fields.isEmpty() || !fields.containsKey("generation")) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    DeckSnapshotMeta meta = toMeta(fields);
                    return Uni.combine().all().unis(
                                    lists.lrange(ReadModelKeys.fresh(viewerProfileId, meta.generation()), 0, -1),
                                    lists.lrange(ReadModelKeys.repeat(viewerProfileId, meta.generation()), 0, -1))
                            .asTuple()
                            .map(tuple -> Optional.of(new DeckSnapshot(
                                    meta, toIds(tuple.getItem1()), toIds(tuple.getItem2()))));
                });
    }

    public Uni<Optional<DeckSnapshotMeta>> markRefreshRequested(
            UUID viewerProfileId,
            String lockToken,
            long expectedGeneration,
            Instant now
    ) {
        String metaKey = ReadModelKeys.viewerMeta(viewerProfileId);
        long retentionSeconds = Duration.ofDays(SNAPSHOT_RETENTION_DAYS).toSeconds();
        return redis.execute(
                        "EVAL", MARK_REFRESH_REQUESTED_SCRIPT, "2",
                        metaKey, ReadModelKeys.buildLock(viewerProfileId),
                        lockToken, Long.toString(expectedGeneration), now.toString(),
                        Long.toString(retentionSeconds))
                .map(response -> response.toInteger())
                .flatMap(result -> result < 0
                        ? Uni.createFrom().item(Optional.empty())
                        : hashes.hgetall(metaKey).map(fields -> Optional.of(toMeta(fields))));
    }

    public Uni<Integer> recordFailure(
            UUID viewerProfileId,
            String lockToken,
            long expectedGeneration,
            Instant now
    ) {
        long retentionSeconds = Duration.ofDays(SNAPSHOT_RETENTION_DAYS).toSeconds();
        return redis.execute(
                        "EVAL", RECORD_FAILURE_SCRIPT, "2",
                        ReadModelKeys.viewerMeta(viewerProfileId),
                        ReadModelKeys.buildLock(viewerProfileId),
                        lockToken, Long.toString(expectedGeneration), now.toString(),
                        Long.toString(retentionSeconds))
                .map(response -> response.toInteger());
    }

    public Uni<Void> markUnavailable(UUID viewerProfileId) {
        return markUnavailable(viewerProfileId, "", -1, Instant.now()).replaceWithVoid();
    }

    public Uni<Long> markUnavailable(
            UUID viewerProfileId,
            String lockToken,
            long expectedGeneration,
            Instant now
    ) {
        long retentionSeconds = Duration.ofDays(SNAPSHOT_RETENTION_DAYS).toSeconds();
        return redis.execute(
                        "EVAL", MARK_UNAVAILABLE_SCRIPT, "2",
                        ReadModelKeys.viewerMeta(viewerProfileId),
                        ReadModelKeys.buildLock(viewerProfileId),
                        lockToken, Long.toString(expectedGeneration), now.toString(),
                        Long.toString(retentionSeconds))
                .map(response -> response.toLong());
    }

    public Uni<Long> install(
            UUID viewerProfileId,
            long previousGeneration,
            List<UUID> fresh,
            List<UUID> repeat,
            DeckState state,
            String sourceBuildTimestamp,
            Instant now
    ) {
        return install(viewerProfileId, previousGeneration, "", fresh, repeat,
                state, sourceBuildTimestamp, now);
    }

    public Uni<Long> install(
            UUID viewerProfileId,
            long previousGeneration,
            String lockToken,
            List<UUID> fresh,
            List<UUID> repeat,
            DeckState state,
            String sourceBuildTimestamp,
            Instant now
    ) {
        List<UUID> boundedFresh = fresh.stream().limit(MAX_FRESH).toList();
        List<UUID> boundedRepeat = repeat.stream().limit(MAX_REPEAT).toList();
        long generation = previousGeneration + 1;
        long retentionSeconds = Duration.ofDays(SNAPSHOT_RETENTION_DAYS).toSeconds();

        List<String> args = new ArrayList<>();
        args.add(INSTALL_SCRIPT);
        args.add("6");
        args.add(ReadModelKeys.viewerMeta(viewerProfileId));
        args.add(ReadModelKeys.fresh(viewerProfileId, generation));
        args.add(ReadModelKeys.repeat(viewerProfileId, generation));
        args.add(ReadModelKeys.fresh(viewerProfileId, previousGeneration));
        args.add(ReadModelKeys.repeat(viewerProfileId, previousGeneration));
        args.add(ReadModelKeys.buildLock(viewerProfileId));
        args.add(Long.toString(previousGeneration));
        args.add(Long.toString(generation));
        args.add(lockToken);
        args.add(now.toString());
        args.add(state.name());
        args.add(sourceBuildTimestamp == null ? "" : sourceBuildTimestamp);
        args.add(Long.toString(retentionSeconds));
        args.add(Integer.toString(boundedFresh.size()));
        boundedFresh.forEach(id -> args.add(id.toString()));
        args.add(Integer.toString(boundedRepeat.size()));
        boundedRepeat.forEach(id -> args.add(id.toString()));
        args.add(Boolean.toString(previousGeneration > 0));
        args.add(Integer.toString(OLD_GENERATION_RETENTION_SECONDS));

        return redis.execute("EVAL", args.toArray(String[]::new))
                .map(response -> response.toLong());
    }

    public Uni<Boolean> acquireBuildLock(UUID viewerProfileId, String token) {
        return redis.execute("SET", ReadModelKeys.buildLock(viewerProfileId), token, "NX", "EX", "30")
                .map(response -> response != null);
    }

    public Uni<Boolean> renewBuildLock(UUID viewerProfileId, String token) {
        return redis.execute("EVAL", RENEW_LOCK_SCRIPT, "1", ReadModelKeys.buildLock(viewerProfileId), token, "30")
                .map(response -> response.toInteger() == 1);
    }

    public Uni<Void> releaseBuildLock(UUID viewerProfileId, String token) {
        return redis.execute("EVAL", RELEASE_LOCK_SCRIPT, "1", ReadModelKeys.buildLock(viewerProfileId), token)
                .replaceWithVoid();
    }

    private DeckSnapshotMeta toMeta(Map<String, String> fields) {
        long generation = parseLong(fields.get("generation"), 0);
        Instant builtAt = parseInstant(fields.get("builtAt"));
        Instant refreshStartedAt = parseInstant(fields.get("refreshStartedAt"));
        DeckState state = parseState(fields.get("state"), generation > 0 ? DeckState.READY : DeckState.REFRESHING);
        return new DeckSnapshotMeta(
                generation,
                builtAt,
                state,
                fields.get("sourceBuildTimestamp"),
                refreshStartedAt,
                (int) parseLong(fields.get("failureCount"), 0),
                Boolean.parseBoolean(fields.getOrDefault("unavailable", "false")));
    }

    private List<UUID> toIds(List<String> ids) {
        return ids == null ? List.of() : ids.stream().map(UUID::fromString).toList();
    }

    private long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private DeckState parseState(String value, DeckState fallback) {
        try {
            return value == null ? fallback : DeckState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
