package com.tinder.deckread.readmodel;

import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** TTL-bounded over-inclusive reverse index for cards in materialized hot windows. */
@ApplicationScoped
public class HotViewerIndex {

    private static final long RETENTION_SECONDS = Duration.ofDays(7).toSeconds();

    private final ReactiveRedisDataSource redis;

    @Inject
    public HotViewerIndex(@RedisClientName("read-model") ReactiveRedisDataSource redis) {
        this.redis = redis;
    }

    public Uni<Void> index(UUID viewerProfileId, Collection<UUID> profileIds) {
        long expiresAtMillis = Instant.now().plusSeconds(RETENTION_SECONDS).toEpochMilli();
        return Multi.createFrom().iterable(profileIds)
                .onItem().transformToUni(profileId -> {
                    String key = ReadModelKeys.hotViewers(profileId);
                    return redis.execute(
                                    "ZADD", key, Long.toString(expiresAtMillis), viewerProfileId.toString())
                            .flatMap(ignored -> redis.execute("EXPIRE", key, Long.toString(RETENTION_SECONDS)))
                            .replaceWithVoid();
                }).merge(16)
                .collect().asList()
                .replaceWithVoid();
    }

    public Uni<Set<UUID>> viewers(UUID profileId) {
        String key = ReadModelKeys.hotViewers(profileId);
        long nowMillis = Instant.now().toEpochMilli();
        return redis.execute("ZREMRANGEBYSCORE", key, "-inf", Long.toString(nowMillis))
                .flatMap(ignored -> redis.execute(
                        "ZRANGEBYSCORE", key, Long.toString(nowMillis + 1), "+inf"))
                .map(values -> values == null
                        ? Set.of()
                        : java.util.stream.StreamSupport.stream(values.spliterator(), false)
                                .map(Response::toString)
                                .map(UUID::fromString)
                                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
