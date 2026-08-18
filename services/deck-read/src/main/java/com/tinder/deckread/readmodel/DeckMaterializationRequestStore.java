package com.tinder.deckread.readmodel;

import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

/** Allocates monotonically increasing viewer-local revisions used to fence stale workers. */
@ApplicationScoped
public class DeckMaterializationRequestStore {

    private static final String REQUEST_SCRIPT = """
            local requested = tonumber(redis.call('HGET', KEYS[1], 'requestedRevision') or '0')
            local published = tonumber(redis.call('HGET', KEYS[1], 'publishedRevision') or '0')
            local retryAfterMillis = tonumber(ARGV[4])
            local nowMillis = tonumber(ARGV[5])
            if retryAfterMillis > 0 and requested > published then
              local lastNotificationMillis = tonumber(redis.call('HGET', KEYS[1], 'notificationAtEpochMs') or '0')
              if lastNotificationMillis > 0 and nowMillis - lastNotificationMillis < retryAfterMillis then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                return { requested, 0 }
              end
              redis.call('HSET', KEYS[1],
                'requestedAt', ARGV[1],
                'requestedReason', ARGV[2],
                'notificationAtEpochMs', ARGV[5])
              redis.call('EXPIRE', KEYS[1], ARGV[3])
              return { requested, 1 }
            end
            local revision = redis.call('HINCRBY', KEYS[1], 'requestedRevision', 1)
            redis.call('HSET', KEYS[1],
              'requestedAt', ARGV[1],
              'requestedReason', ARGV[2],
              'notificationAtEpochMs', ARGV[5])
            if tonumber(redis.call('HGET', KEYS[1], 'generation') or '0') > 0 then
              redis.call('HSET', KEYS[1], 'state', 'REFRESHING')
              redis.call('HSETNX', KEYS[1], 'refreshStartedAt', ARGV[1])
            end
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return { revision, 1 }
            """;

    private final ReactiveRedisDataSource redis;

    @Inject
    public DeckMaterializationRequestStore(
            @RedisClientName("read-model") ReactiveRedisDataSource redis
    ) {
        this.redis = redis;
    }

    public Uni<Long> request(UUID viewerProfileId, String reason, Instant now) {
        return allocate(viewerProfileId, reason, now, Duration.ZERO)
                .map(RequestAllocation::revision);
    }

    /**
     * Coalesces repeated repair triggers while a revision is still pending. After the retry
     * interval it republishes the same revision instead of fencing an in-flight worker.
     */
    public Uni<RequestAllocation> requestCoalesced(
            UUID viewerProfileId,
            String reason,
            Instant now,
            Duration notificationRetryInterval
    ) {
        return allocate(viewerProfileId, reason, now, notificationRetryInterval);
    }

    private Uni<RequestAllocation> allocate(
            UUID viewerProfileId,
            String reason,
            Instant now,
            Duration notificationRetryInterval
    ) {
        return redis.execute(
                        "EVAL", REQUEST_SCRIPT, "1", ReadModelKeys.materializedMeta(viewerProfileId),
                        now.toString(), reason,
                        Long.toString(Duration.ofDays(DeckSnapshotStore.SNAPSHOT_RETENTION_DAYS).toSeconds()),
                        Long.toString(Math.max(0, notificationRetryInterval.toMillis())),
                        Long.toString(now.toEpochMilli()))
                .map(response -> new RequestAllocation(
                        response.get(0).toLong(), response.get(1).toInteger() == 1));
    }

    public Uni<Long> requestedRevision(UUID viewerProfileId) {
        return redis.execute(
                        "HGET", ReadModelKeys.materializedMeta(viewerProfileId), "requestedRevision")
                .map(response -> response == null ? 0L : response.toLong());
    }

    public record RequestAllocation(long revision, boolean enqueue) {
    }
}
