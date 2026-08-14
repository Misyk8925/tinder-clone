package com.tinder.deckread.readmodel;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.SwipeSavedEvent;
import com.tinder.deckread.messaging.MatchCreatedEvent;
import io.quarkus.redis.client.RedisClientName;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@Tag("acceptance")
@DisplayName("Feature: immutable materialized Deck generations")
class MaterializedDeckStoreAcceptanceTest {

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Inject MaterializedDeckStore store;
    @Inject DeckMaterializationRequestStore requests;
    @Inject ViewerMutationStore mutations;
    @Inject HotViewerIndex hotViewers;

    @Inject
    @RedisClientName("read-model")
    RedisDataSource redis;

    @BeforeEach
    void clear() {
        redis.flushall();
    }

    @Test
    @DisplayName("Scenario: Given 120 cards, when a generation commits, then one page read returns the ready window and tail remains addressable")
    void materializesReadyWindowAndTail() {
        long revision = requests.request(VIEWER, "TEST", Instant.now()).await().indefinitely();
        List<DeckCardDto> cards = cards(120);

        long generation = store.install(
                VIEWER, revision, cards, DeckState.READY, "100", Instant.now())
                .await().indefinitely();

        MaterializedDeckSlice first = store.readPage(VIEWER, 0, 0, 20).await().indefinitely();
        assertThat(generation).isPositive();
        assertThat(first.cards()).extracting(DeckCardDto::profileId)
                .containsExactlyElementsOf(cards.subList(0, 20).stream().map(DeckCardDto::profileId).toList());
        assertThat(first.nextPosition()).isEqualTo(20);
        assertThat(first.totalCount()).isEqualTo(120);
        assertThat(store.readTail(VIEWER, generation, 0, 20).await().indefinitely())
                .containsExactlyElementsOf(cards.subList(100, 120).stream().map(DeckCardDto::profileId).toList());
    }

    @Test
    @DisplayName("Scenario: every supported limit from 1 through 100 is served from the ready generation")
    void servesEverySupportedReadyPageLimit() {
        long revision = requests.request(VIEWER, "TEST", Instant.now()).await().indefinitely();
        store.install(VIEWER, revision, cards(100), DeckState.READY, "100", Instant.now())
                .await().indefinitely();

        for (int limit = 1; limit <= 100; limit++) {
            MaterializedDeckSlice page = store.readPage(VIEWER, 0, 0, limit).await().indefinitely();
            assertThat(page.cards()).hasSize(limit);
            assertThat(page.nextPosition()).isEqualTo(limit);
        }
    }

    @Test
    @DisplayName("Scenario: Given a newer request, when an older worker commits, then revision fencing rejects it")
    void staleWorkerCannotPublish() {
        long staleRevision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        requests.request(VIEWER, "SECOND", Instant.now()).await().indefinitely();

        long result = store.install(
                VIEWER, staleRevision, cards(1), DeckState.READY, "1", Instant.now())
                .await().indefinitely();

        assertThat(result).isNegative();
        assertThat(store.meta(VIEWER).await().indefinitely()).isEmpty();
    }

    @Test
    @DisplayName("Scenario: Given a committed card, when its swipe projection arrives, then the fast Lua read excludes it immediately")
    void fastReadAppliesImmediateSwipeExclusion() {
        List<DeckCardDto> cards = cards(2);
        long revision = requests.request(VIEWER, "TEST", Instant.now()).await().indefinitely();
        store.install(VIEWER, revision, cards, DeckState.READY, "1", Instant.now())
                .await().indefinitely();

        mutations.applySwipe(new SwipeSavedEvent(
                UUID.randomUUID().toString(), VIEWER.toString(),
                cards.get(0).profileId().toString(), false, Instant.now().toEpochMilli()))
                .await().indefinitely();

        MaterializedDeckSlice page = store.readPage(VIEWER, 0, 0, 20).await().indefinitely();
        assertThat(page.cards()).extracting(DeckCardDto::profileId)
                .containsExactly(cards.get(1).profileId());
        assertThat(page.nextPosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario: Given a cursor from an old generation, when the new generation is read, then position resets atomically")
    void oldCursorResetsToCurrentGeneration() {
        long firstRevision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        long firstGeneration = store.install(
                VIEWER, firstRevision, cards(2), DeckState.READY, "1", Instant.now())
                .await().indefinitely();
        long secondRevision = requests.request(VIEWER, "SECOND", Instant.now()).await().indefinitely();
        long secondGeneration = store.install(
                VIEWER, secondRevision, cards(3), DeckState.READY, "2", Instant.now())
                .await().indefinitely();

        MaterializedDeckSlice page = store.readPage(VIEWER, firstGeneration, 1, 20)
                .await().indefinitely();

        assertThat(page.generation()).isEqualTo(secondGeneration);
        assertThat(page.cursorReset()).isTrue();
        assertThat(page.cards()).hasSize(3);
    }

    @Test
    @DisplayName("Scenario: Given an active generation, when refresh is requested, then the old page remains readable as REFRESHING")
    void requestMarksPublishedGenerationRefreshing() {
        long revision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        store.install(VIEWER, revision, cards(2), DeckState.READY, "1", Instant.now())
                .await().indefinitely();

        requests.request(VIEWER, "PROFILE_CHANGED", Instant.now()).await().indefinitely();

        MaterializedDeckSlice page = store.readPage(VIEWER, 0, 0, 20).await().indefinitely();
        assertThat(page.state()).isEqualTo(DeckState.REFRESHING);
        assertThat(page.cards()).hasSize(2);
    }

    @Test
    @DisplayName("Scenario: Given ready cards, when match and delete suppression arrive, then both disappear before rebuild")
    void fastReadAppliesMatchAndDeleteSuppression() {
        List<DeckCardDto> cards = cards(3);
        long revision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        store.install(VIEWER, revision, cards, DeckState.READY, "1", Instant.now())
                .await().indefinitely();

        mutations.applyMatch(new MatchCreatedEvent(
                        UUID.randomUUID().toString(), VIEWER.toString(),
                        cards.get(0).profileId().toString(), Instant.now()))
                .await().indefinitely();
        mutations.suppress(VIEWER, cards.get(1).profileId()).await().indefinitely();

        MaterializedDeckSlice page = store.readPage(VIEWER, 0, 0, 20).await().indefinitely();
        assertThat(page.cards()).extracting(DeckCardDto::profileId)
                .containsExactly(cards.get(2).profileId());
    }

    @Test
    @DisplayName("Scenario: Given a generation switch, then old immutable data is retained for the rollback window")
    void oldGenerationReceivesRetentionTtlAfterAtomicSwitch() {
        long firstRevision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        long firstGeneration = store.install(
                VIEWER, firstRevision, cards(2), DeckState.READY, "1", Instant.now())
                .await().indefinitely();
        long secondRevision = requests.request(VIEWER, "SECOND", Instant.now()).await().indefinitely();
        store.install(VIEWER, secondRevision, cards(3), DeckState.READY, "2", Instant.now())
                .await().indefinitely();

        int orderTtl = redis.execute(
                "TTL", ReadModelKeys.materializedOrder(VIEWER, firstGeneration)).toInteger();
        int cardsTtl = redis.execute(
                "TTL", ReadModelKeys.materializedCards(VIEWER, firstGeneration)).toInteger();
        assertThat(orderTtl).isBetween(1, 30 * 60);
        assertThat(cardsTtl).isBetween(1, 30 * 60);
    }

    @Test
    @DisplayName("Scenario: Given a pending API repair, repeated GET triggers reuse its revision and retry only after the notification window")
    void coalescesApiRepairWithoutFencingTheWorker() {
        Instant firstAttempt = Instant.parse("2026-08-14T08:00:00Z");

        DeckMaterializationRequestStore.RequestAllocation first = requests.requestCoalesced(
                VIEWER, "API_MISS", firstAttempt, Duration.ofSeconds(5)).await().indefinitely();
        DeckMaterializationRequestStore.RequestAllocation duplicate = requests.requestCoalesced(
                VIEWER, "API_MISS", firstAttempt.plusSeconds(1), Duration.ofSeconds(5)).await().indefinitely();
        DeckMaterializationRequestStore.RequestAllocation retry = requests.requestCoalesced(
                VIEWER, "API_MISS", firstAttempt.plusSeconds(6), Duration.ofSeconds(5)).await().indefinitely();

        assertThat(first.enqueue()).isTrue();
        assertThat(duplicate.enqueue()).isFalse();
        assertThat(retry.enqueue()).isTrue();
        assertThat(duplicate.revision()).isEqualTo(first.revision());
        assertThat(retry.revision()).isEqualTo(first.revision());
        assertThat(requests.requestedRevision(VIEWER).await().indefinitely()).isEqualTo(first.revision());

        store.install(VIEWER, first.revision(), cards(1), DeckState.READY, "1", firstAttempt.plusSeconds(7))
                .await().indefinitely();
        DeckMaterializationRequestStore.RequestAllocation afterPublish = requests.requestCoalesced(
                VIEWER, "API_STALE", firstAttempt.plusSeconds(8), Duration.ofSeconds(5)).await().indefinitely();

        assertThat(afterPublish.enqueue()).isTrue();
        assertThat(afterPublish.revision()).isEqualTo(first.revision() + 1);
    }

    @Test
    @DisplayName("Scenario: Given a popular profile index, expired viewers are removed without expiring active viewers")
    void hotReverseIndexExpiresIndividualViewerMemberships() {
        UUID profile = UUID.randomUUID();
        UUID expiredViewer = UUID.randomUUID();
        UUID activeViewer = UUID.randomUUID();
        String key = ReadModelKeys.hotViewers(profile);
        long now = Instant.now().toEpochMilli();
        redis.execute("ZADD", key, Long.toString(now - 1), expiredViewer.toString());
        redis.execute("ZADD", key, Long.toString(now + Duration.ofDays(1).toMillis()), activeViewer.toString());

        assertThat(hotViewers.viewers(profile).await().indefinitely())
                .containsExactly(activeViewer);
        assertThat(redis.execute("ZSCORE", key, expiredViewer.toString())).isNull();
    }

    @Test
    @DisplayName("Scenario: Given a failed refresh, the current generation stays readable and failure counters are recorded")
    void failedRefreshKeepsCurrentGeneration() {
        long firstRevision = requests.request(VIEWER, "FIRST", Instant.now()).await().indefinitely();
        long generation = store.install(
                VIEWER, firstRevision, cards(2), DeckState.READY, "1", Instant.now())
                .await().indefinitely();
        long failedRevision = requests.request(VIEWER, "PROFILE_CHANGED", Instant.now())
                .await().indefinitely();

        store.recordFailure(VIEWER, failedRevision, Instant.now()).await().indefinitely();

        MaterializedDeckSlice page = store.readPage(VIEWER, 0, 0, 20).await().indefinitely();
        assertThat(page.generation()).isEqualTo(generation);
        assertThat(page.state()).isEqualTo(DeckState.REFRESHING);
        assertThat(page.cards()).hasSize(2);
        assertThat(redis.execute(
                "HGET", ReadModelKeys.materializedMeta(VIEWER), "failureCount").toInteger()).isEqualTo(1);
    }

    private List<DeckCardDto> cards(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    UUID id = UUID.nameUUIDFromBytes(("materialized-card-" + index).getBytes());
                    return new DeckCardDto(
                            id, "candidate-" + index, 25, "Vienna", "bio", true,
                            new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
                })
                .toList();
    }
}
