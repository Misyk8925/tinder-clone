package com.tinder.deckread.service;

import com.tinder.contracts.deck.DeckRedisKeys;
import com.tinder.deckread.messaging.DeckMaterializationRequester;
import com.tinder.deckread.messaging.MaterializationReason;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.UUID;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/** Rate-limited SCAN repair for build events lost after the Deck Redis commit. */
@ApplicationScoped
public class DeckSourceReconciler {

    private static final String LEASE_KEY = "dr:reconciliation:lease";
    private static final String CURSOR_KEY = "dr:reconciliation:cursor";
    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private final ReactiveRedisDataSource sourceRedis;
    private final ReactiveRedisDataSource readModelRedis;
    private final ReactiveValueCommands<String, String> sourceValues;
    private final ReactiveValueCommands<String, String> readModelValues;
    private final DeckMaterializationRequester requester;
    private final MaterializedDeckStore materialized;
    private final Counter repairs;

    @ConfigProperty(name = "deck-read.reconciliation.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "deck-read.reconciliation.scan-count", defaultValue = "100")
    int scanCount;

    @Inject
    public DeckSourceReconciler(
            @RedisClientName("deck-source") ReactiveRedisDataSource sourceRedis,
            @RedisClientName("read-model") ReactiveRedisDataSource readModelRedis,
            DeckMaterializationRequester requester,
            MaterializedDeckStore materialized,
            MeterRegistry meters
    ) {
        this.sourceRedis = sourceRedis;
        this.readModelRedis = readModelRedis;
        this.sourceValues = sourceRedis.value(String.class);
        this.readModelValues = readModelRedis.value(String.class);
        this.requester = requester;
        this.materialized = materialized;
        this.repairs = meters.counter("deck_read_reconciliation_repairs");
    }

    @Scheduled(every = "${deck-read.reconciliation.interval}", concurrentExecution = SKIP)
    Uni<Void> reconcile() {
        if (!enabled) {
            return Uni.createFrom().voidItem();
        }
        String token = UUID.randomUUID().toString();
        return readModelRedis.execute("SET", LEASE_KEY, token, "NX", "EX", "55")
                .flatMap(acquired -> acquired == null
                        ? Uni.createFrom().voidItem()
                        : scanOnce()
                                .eventually(() -> readModelRedis.execute(
                                        "EVAL", RELEASE_SCRIPT, "1", LEASE_KEY, token).replaceWithVoid()));
    }

    private Uni<Void> scanOnce() {
        return readModelValues.get(CURSOR_KEY)
                .map(cursor -> cursor == null ? "0" : cursor)
                .flatMap(cursor -> sourceRedis.execute(
                        "SCAN", cursor, "MATCH", DeckRedisKeys.PRIMARY_DECK_PREFIX + "build:ts:*",
                        "COUNT", Integer.toString(Math.max(1, scanCount))))
                .flatMap(response -> {
                    String nextCursor = response.get(0).toString();
                    List<String> keys = response.get(1) == null
                            ? List.of()
                            : java.util.stream.StreamSupport.stream(
                                            response.get(1).spliterator(), false)
                                    .map(Response::toString)
                                    .toList();
                    return Multi.createFrom().iterable(keys)
                            .onItem().transformToUniAndConcatenate(this::repairIfNeeded)
                            .collect().asList()
                            .flatMap(ignored -> readModelValues.set(CURSOR_KEY, nextCursor));
                });
    }

    private Uni<Void> repairIfNeeded(String timestampKey) {
        String rawId = timestampKey.substring("deck:build:ts:".length());
        UUID viewer;
        try {
            viewer = UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return Uni.createFrom().voidItem();
        }
        UUID resolvedViewer = viewer;
        return sourceValues.get(timestampKey)
                .flatMap(sourceTimestamp -> materialized.meta(resolvedViewer)
                        .flatMap(meta -> meta.isPresent()
                                && java.util.Objects.equals(
                                        meta.orElseThrow().sourceBuildTimestamp(), sourceTimestamp)
                                        ? Uni.createFrom().voidItem()
                                        : requester.request(
                                                resolvedViewer,
                                                MaterializationReason.RECONCILIATION,
                                                sourceTimestamp)
                                        .invoke(() -> repairs.increment())));
    }
}
