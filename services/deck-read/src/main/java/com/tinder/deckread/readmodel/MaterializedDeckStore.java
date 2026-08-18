package com.tinder.deckread.readmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Immutable-generation materialized pages optimized for one viewer-local Redis read. */
@ApplicationScoped
public class MaterializedDeckStore {

    public static final int READY_WINDOW = 100;
    public static final int TOTAL_WINDOW = 500;
    private static final long RETENTION_SECONDS = Duration.ofDays(7).toSeconds();
    private static final long OLD_GENERATION_SECONDS = Duration.ofMinutes(30).toSeconds();
    private static final long STAGING_SECONDS = Duration.ofMinutes(30).toSeconds();

    private static final String COMMIT_SCRIPT = """
            local requested = tonumber(redis.call('HGET', KEYS[1], 'requestedRevision') or '0')
            local published = tonumber(redis.call('HGET', KEYS[1], 'publishedRevision') or '0')
            if requested ~= tonumber(ARGV[1]) then return -1 end
            if published >= tonumber(ARGV[1]) then return -2 end
            redis.call('HSET', KEYS[1],
              'generation', ARGV[2],
              'publishedRevision', ARGV[1],
              'builtAt', ARGV[3],
              'state', ARGV[4],
              'sourceBuildTimestamp', ARGV[5],
              'readyCount', ARGV[6],
              'totalCount', ARGV[7],
              'unavailable', 'false')
            redis.call('HDEL', KEYS[1], 'refreshStartedAt', 'lastFailureAt')
            redis.call('EXPIRE', KEYS[1], ARGV[8])
            redis.call('EXPIRE', KEYS[2], ARGV[8])
            redis.call('EXPIRE', KEYS[3], ARGV[8])
            redis.call('EXPIRE', KEYS[4], ARGV[8])
            if tonumber(ARGV[9]) > 0 then
              redis.call('EXPIRE', KEYS[5], ARGV[10])
              redis.call('EXPIRE', KEYS[6], ARGV[10])
              redis.call('EXPIRE', KEYS[7], ARGV[10])
            end
            return tonumber(ARGV[2])
            """;

    private static final String READ_PAGE_SCRIPT = """
            local meta = KEYS[1]
            local generation = tonumber(redis.call('HGET', meta, 'generation') or '0')
            if generation == 0 then return {} end
            local requestedGeneration = tonumber(ARGV[1])
            local position = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local cursorReset = 0
            if requestedGeneration > 0 and requestedGeneration ~= generation then
              cursorReset = 1
              position = 0
            end
            local prefix = string.sub(meta, 1, string.len(meta) - 5)
            local orderKey = prefix .. ':order:' .. generation
            local cardsKey = prefix .. ':cards:' .. generation
            local viewerPrefix = string.sub(prefix, 1, string.len(prefix) - 2)
            local swipesKey = viewerPrefix .. ':swipes'
            local matchedKey = viewerPrefix .. ':matched'
            local suppressedKey = viewerPrefix .. ':suppressed'
            local readyCount = tonumber(redis.call('HGET', meta, 'readyCount') or '0')
            local totalCount = tonumber(redis.call('HGET', meta, 'totalCount') or '0')
            local result = {
              tostring(generation), tostring(cursorReset),
              redis.call('HGET', meta, 'state') or 'READY',
              redis.call('HGET', meta, 'builtAt') or '',
              redis.call('HGET', meta, 'sourceBuildTimestamp') or '',
              tostring(readyCount), tostring(totalCount), tostring(position),
              redis.call('HGET', meta, 'unavailable') or 'false'
            }
            if position >= readyCount then return result end
            local ids = redis.call('ZRANGE', orderKey, position, readyCount - 1)
            local nextPosition = position
            local emitted = 0
            for _, id in ipairs(ids) do
              nextPosition = nextPosition + 1
              if not redis.call('HGET', swipesKey, id)
                  and redis.call('SISMEMBER', matchedKey, id) == 0
                  and redis.call('SISMEMBER', suppressedKey, id) == 0 then
                local card = redis.call('HGET', cardsKey, id)
                if card then
                  table.insert(result, card)
                  emitted = emitted + 1
                  if emitted >= limit then break end
                end
              end
            end
            result[8] = tostring(nextPosition)
            return result
            """;

    private static final String RECORD_FAILURE_SCRIPT = """
            local requested = tonumber(redis.call('HGET', KEYS[1], 'requestedRevision') or '0')
            if requested ~= tonumber(ARGV[1]) then return -1 end
            local failureCount = redis.call('HINCRBY', KEYS[1], 'failureCount', 1)
            redis.call('HSET', KEYS[1], 'lastFailureAt', ARGV[2])
            if tonumber(redis.call('HGET', KEYS[1], 'generation') or '0') > 0 then
              redis.call('HSET', KEYS[1], 'state', 'REFRESHING')
            end
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return failureCount
            """;

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hashes;
    private final ObjectMapper mapper;
    private final HotViewerIndex hotViewers;
    private final Timer pageLatency;

    @Inject
    public MaterializedDeckStore(
            @RedisClientName("read-model") ReactiveRedisDataSource redis,
            ObjectMapper mapper,
            HotViewerIndex hotViewers,
            MeterRegistry meters
    ) {
        this.redis = redis;
        this.hashes = redis.hash(String.class);
        this.mapper = mapper;
        this.hotViewers = hotViewers;
        this.pageLatency = meters.timer("deck_read_redis_page_latency");
    }

    public Uni<Optional<MaterializedDeckMeta>> meta(UUID viewerProfileId) {
        return hashes.hgetall(ReadModelKeys.materializedMeta(viewerProfileId))
                .map(fields -> {
                    if (fields == null || fields.isEmpty()) {
                        return Optional.empty();
                    }
                    long generation = parseLong(fields.get("generation"), 0);
                    if (generation == 0) {
                        return Optional.empty();
                    }
                    return Optional.of(new MaterializedDeckMeta(
                            generation,
                            parseLong(fields.get("requestedRevision"), 0),
                            parseLong(fields.get("publishedRevision"), 0),
                            parseInstant(fields.get("builtAt")),
                            parseState(fields.get("state")),
                            fields.getOrDefault("sourceBuildTimestamp", ""),
                            (int) parseLong(fields.get("readyCount"), 0),
                            (int) parseLong(fields.get("totalCount"), 0),
                            Boolean.parseBoolean(fields.getOrDefault("unavailable", "false"))));
                });
    }

    public Uni<MaterializedDeckSlice> readPage(
            UUID viewerProfileId,
            long requestedGeneration,
            int position,
            int limit
    ) {
        Timer.Sample sample = Timer.start();
        return redis.execute(
                        "EVAL", READ_PAGE_SCRIPT, "1", ReadModelKeys.materializedMeta(viewerProfileId),
                        Long.toString(requestedGeneration), Integer.toString(position), Integer.toString(limit))
                .map(this::toSlice)
                .onItemOrFailure().invoke((ignored, failure) -> sample.stop(pageLatency));
    }

    public Uni<List<UUID>> readTail(UUID viewerProfileId, long generation, int offset, int limit) {
        if (limit <= 0) {
            return Uni.createFrom().item(List.of());
        }
        long end = (long) Math.max(0, offset) + limit - 1;
        return redis.execute(
                        "LRANGE", ReadModelKeys.materializedTail(viewerProfileId, generation),
                        Integer.toString(Math.max(0, offset)), Long.toString(end))
                .map(response -> response == null
                        ? List.of()
                        : java.util.stream.StreamSupport.stream(response.spliterator(), false)
                                .map(value -> UUID.fromString(value.toString()))
                                .toList());
    }

    public Uni<Void> recordFailure(UUID viewerProfileId, long requestedRevision, Instant now) {
        return redis.execute(
                        "EVAL", RECORD_FAILURE_SCRIPT, "1",
                        ReadModelKeys.materializedMeta(viewerProfileId),
                        Long.toString(requestedRevision), now.toString(), Long.toString(RETENTION_SECONDS))
                .replaceWithVoid();
    }

    public Uni<Long> install(
            UUID viewerProfileId,
            long requestedRevision,
            List<DeckCardDto> orderedCards,
            DeckState state,
            String sourceBuildTimestamp,
            Instant now
    ) {
        List<DeckCardDto> bounded = orderedCards.stream().limit(TOTAL_WINDOW).toList();
        List<DeckCardDto> ready = bounded.stream().limit(READY_WINDOW).toList();
        List<UUID> tail = bounded.stream().skip(READY_WINDOW).map(DeckCardDto::profileId).toList();
        return meta(viewerProfileId)
                .flatMap(previous -> allocateGeneration(viewerProfileId)
                        .flatMap(generation -> stage(viewerProfileId, generation, ready, tail)
                                // The reverse index is deliberately over-inclusive and TTL-bounded.
                                // Write it before the pointer commit so a post-commit index failure
                                // can never leave a published hot card without profile fan-out.
                                .flatMap(ignored -> hotViewers.index(
                                        viewerProfileId,
                                        ready.stream().map(DeckCardDto::profileId).toList()))
                                .flatMap(ignored -> commit(
                                        viewerProfileId, previous.map(MaterializedDeckMeta::generation).orElse(0L),
                                        generation, requestedRevision, ready.size(), bounded.size(), state,
                                        sourceBuildTimestamp, now))
                                .flatMap(result -> result < 0
                                        ? discard(viewerProfileId, generation).replaceWith(result)
                                        : Uni.createFrom().item(result))));
    }

    private Uni<Long> allocateGeneration(UUID viewerProfileId) {
        return redis.execute("HINCRBY", ReadModelKeys.materializedMeta(viewerProfileId), "nextGeneration", "1")
                .map(Response::toLong);
    }

    private Uni<Void> stage(
            UUID viewerProfileId,
            long generation,
            List<DeckCardDto> ready,
            List<UUID> tail
    ) {
        String orderKey = ReadModelKeys.materializedOrder(viewerProfileId, generation);
        String cardsKey = ReadModelKeys.materializedCards(viewerProfileId, generation);
        String tailKey = ReadModelKeys.materializedTail(viewerProfileId, generation);
        Uni<Void> orderWrite = ready.isEmpty()
                ? Uni.createFrom().voidItem()
                : redis.execute("ZADD", zaddArgs(orderKey, ready))
                        .flatMap(ignored -> redis.execute(
                                "EXPIRE", orderKey, Long.toString(STAGING_SECONDS)))
                        .replaceWithVoid();
        Uni<Void> cardWrite = ready.isEmpty()
                ? Uni.createFrom().voidItem()
                : redis.execute("HSET", cardArgs(cardsKey, ready))
                        .flatMap(ignored -> redis.execute(
                                "EXPIRE", cardsKey, Long.toString(STAGING_SECONDS)))
                        .replaceWithVoid();
        Uni<Void> tailWrite = tail.isEmpty()
                ? Uni.createFrom().voidItem()
                : redis.execute("RPUSH", listArgs(tailKey, tail))
                        .flatMap(ignored -> redis.execute(
                                "EXPIRE", tailKey, Long.toString(STAGING_SECONDS)))
                        .replaceWithVoid();
        return Uni.combine().all().unis(orderWrite, cardWrite, tailWrite).discardItems();
    }

    private Uni<Long> commit(
            UUID viewerProfileId,
            long oldGeneration,
            long generation,
            long requestedRevision,
            int readyCount,
            int totalCount,
            DeckState state,
            String sourceBuildTimestamp,
            Instant now
    ) {
        return redis.execute(
                        "EVAL", COMMIT_SCRIPT, "7",
                        ReadModelKeys.materializedMeta(viewerProfileId),
                        ReadModelKeys.materializedOrder(viewerProfileId, generation),
                        ReadModelKeys.materializedCards(viewerProfileId, generation),
                        ReadModelKeys.materializedTail(viewerProfileId, generation),
                        ReadModelKeys.materializedOrder(viewerProfileId, oldGeneration),
                        ReadModelKeys.materializedCards(viewerProfileId, oldGeneration),
                        ReadModelKeys.materializedTail(viewerProfileId, oldGeneration),
                        Long.toString(requestedRevision), Long.toString(generation), now.toString(), state.name(),
                        sourceBuildTimestamp == null ? "" : sourceBuildTimestamp,
                        Integer.toString(readyCount), Integer.toString(totalCount),
                        Long.toString(RETENTION_SECONDS), Long.toString(oldGeneration),
                        Long.toString(OLD_GENERATION_SECONDS))
                .map(Response::toLong);
    }

    private Uni<Void> discard(UUID viewerProfileId, long generation) {
        return redis.execute(
                        "DEL",
                        ReadModelKeys.materializedOrder(viewerProfileId, generation),
                        ReadModelKeys.materializedCards(viewerProfileId, generation),
                        ReadModelKeys.materializedTail(viewerProfileId, generation))
                .replaceWithVoid();
    }

    private MaterializedDeckSlice toSlice(Response response) {
        if (response == null || response.size() == 0) {
            return null;
        }
        List<DeckCardDto> cards = new ArrayList<>();
        for (int index = 9; index < response.size(); index++) {
            cards.add(readCard(response.get(index).toString()));
        }
        return new MaterializedDeckSlice(
                cards,
                response.get(0).toLong(),
                response.get(1).toInteger() == 1,
                response.get(7).toInteger(),
                response.get(6).toInteger(),
                parseState(response.get(2).toString()),
                parseInstant(response.get(3).toString()),
                response.get(4).toString(),
                Boolean.parseBoolean(response.get(8).toString()));
    }

    private String[] zaddArgs(String key, List<DeckCardDto> cards) {
        List<String> args = new ArrayList<>();
        args.add(key);
        for (int index = 0; index < cards.size(); index++) {
            args.add(Integer.toString(index));
            args.add(cards.get(index).profileId().toString());
        }
        return args.toArray(String[]::new);
    }

    private String[] cardArgs(String key, List<DeckCardDto> cards) {
        List<String> args = new ArrayList<>();
        args.add(key);
        cards.forEach(card -> {
            args.add(card.profileId().toString());
            args.add(writeCard(card));
        });
        return args.toArray(String[]::new);
    }

    private String[] listArgs(String key, List<UUID> ids) {
        List<String> args = new ArrayList<>();
        args.add(key);
        ids.forEach(id -> args.add(id.toString()));
        return args.toArray(String[]::new);
    }

    private String writeCard(DeckCardDto card) {
        try {
            return mapper.writeValueAsString(card);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize materialized deck card", error);
        }
    }

    private DeckCardDto readCard(String value) {
        try {
            return mapper.readValue(value, DeckCardDto.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to deserialize materialized deck card", error);
        }
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

    private DeckState parseState(String value) {
        try {
            return value == null ? DeckState.READY : DeckState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return DeckState.READY;
        }
    }
}
