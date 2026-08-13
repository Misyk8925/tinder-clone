package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckState;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("acceptance")
@DisplayName("Feature: Deck snapshot writes are fenced by the active viewer build token")
class DeckSnapshotStoreAcceptanceTest {

    @Inject
    DeckSnapshotStore snapshots;

    @Inject
    @RedisClientName("read-model")
    RedisDataSource redis;

    @Inject
    @RedisClientName("read-model")
    ReactiveRedisDataSource reactiveRedis;

    @BeforeEach
    void flush() {
        redis.flushall();
    }

    @Test
    @DisplayName("Scenario: Given a replacement lock owner, when the expired builder finishes last, then its snapshot and failure state are rejected")
    void expiredBuilderCannotOverwriteTheReplacementBuilder() {
        // Given
        UUID viewer = UUID.randomUUID();
        UUID currentCard = UUID.randomUUID();
        UUID staleCard = UUID.randomUUID();
        assertThat(snapshots.acquireBuildLock(viewer, "old-token").await().indefinitely()).isTrue();
        redis.key().del(ReadModelKeys.buildLock(viewer));
        assertThat(snapshots.acquireBuildLock(viewer, "new-token").await().indefinitely()).isTrue();

        // When
        long currentResult = snapshots.install(
                viewer, 0, "new-token", List.of(currentCard), List.of(),
                DeckState.READY, "source-current", Instant.now()).await().indefinitely();
        long staleResult = snapshots.install(
                viewer, 0, "old-token", List.of(staleCard), List.of(),
                DeckState.READY, "source-stale", Instant.now()).await().indefinitely();
        long staleFailure = snapshots.markUnavailable(
                viewer, "old-token", 1, Instant.now()).await().indefinitely();
        int staleFailureCount = snapshots.recordFailure(
                viewer, "old-token", 1, Instant.now()).await().indefinitely();
        var staleRefresh = snapshots.markRefreshRequested(
                viewer, "old-token", 1, Instant.now()).await().indefinitely();

        // Then
        assertThat(currentResult).isEqualTo(1);
        assertThat(staleResult).isEqualTo(-1);
        assertThat(staleFailure).isEqualTo(-1);
        assertThat(staleFailureCount).isEqualTo(-1);
        assertThat(staleRefresh).isEmpty();
        DeckSnapshot installed = snapshots.load(viewer).await().indefinitely().orElseThrow();
        assertThat(installed.fresh()).containsExactly(currentCard);
        assertThat(installed.meta().sourceBuildTimestamp()).isEqualTo("source-current");
        assertThat(installed.meta().state()).isEqualTo(DeckState.READY);
        assertThat(installed.meta().unavailable()).isFalse();
        assertThat(installed.meta().failureCount()).isZero();
    }

    @Test
    @DisplayName("Scenario: Given two replica-local stores share Redis, when ownership changes, then generations remain monotonic and stale writes are fenced")
    void twoReplicaStoresKeepOneMonotonicViewerSnapshot() {
        // Given
        DeckSnapshotStore replicaA = new DeckSnapshotStore(reactiveRedis);
        DeckSnapshotStore replicaB = new DeckSnapshotStore(reactiveRedis);
        UUID viewer = UUID.randomUUID();
        UUID generationOneCard = UUID.randomUUID();
        UUID generationTwoCard = UUID.randomUUID();
        assertThat(replicaA.acquireBuildLock(viewer, "replica-a").await().indefinitely()).isTrue();

        // When: A publishes generation 1, then ownership moves to B.
        assertThat(replicaA.install(
                viewer, 0, "replica-a", List.of(generationOneCard), List.of(),
                DeckState.READY, "source-1", Instant.now()).await().indefinitely()).isEqualTo(1);
        replicaA.releaseBuildLock(viewer, "replica-a").await().indefinitely();
        assertThat(replicaB.acquireBuildLock(viewer, "replica-b").await().indefinitely()).isTrue();
        assertThat(replicaB.install(
                viewer, 1, "replica-b", List.of(generationTwoCard), List.of(),
                DeckState.READY, "source-2", Instant.now()).await().indefinitely()).isEqualTo(2);
        long staleReplicaResult = replicaA.install(
                viewer, 1, "replica-a", List.of(generationOneCard), List.of(),
                DeckState.READY, "source-stale", Instant.now()).await().indefinitely();

        // Then
        assertThat(staleReplicaResult).isEqualTo(-1);
        DeckSnapshot finalSnapshot = replicaB.load(viewer).await().indefinitely().orElseThrow();
        assertThat(finalSnapshot.meta().generation()).isEqualTo(2);
        assertThat(finalSnapshot.meta().sourceBuildTimestamp()).isEqualTo("source-2");
        assertThat(finalSnapshot.fresh()).containsExactly(generationTwoCard);
    }
}
