package com.tinder.deckread.service;

import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshot;
import com.tinder.deckread.readmodel.DeckSnapshotMeta;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.redis.DeckRedisReader;
import com.tinder.deckread.redis.SourceDeckSnapshot;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("acceptance")
@DisplayName("Feature: Deck Read distinguishes successful empty builds from failures and bounds repeat fallback")
class DeckSnapshotBuilderAcceptanceTest {

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CANDIDATE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REPEAT_CANDIDATE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private DeckSnapshotStore snapshots;
    private DeckRedisReader sourceRedis;
    private ProfileProjectionStore profiles;
    private ViewerMutationStore viewerMutations;
    private ReadModelReadiness readiness;
    private DeckEnsureClient deckEnsure;
    private DeckSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        snapshots = mock(DeckSnapshotStore.class);
        sourceRedis = mock(DeckRedisReader.class);
        profiles = mock(ProfileProjectionStore.class);
        viewerMutations = mock(ViewerMutationStore.class);
        readiness = mock(ReadModelReadiness.class);
        deckEnsure = mock(DeckEnsureClient.class);

        builder = new DeckSnapshotBuilder();
        builder.snapshots = snapshots;
        builder.sourceRedis = sourceRedis;
        builder.profiles = profiles;
        builder.viewerMutations = viewerMutations;
        builder.readiness = readiness;
        builder.deckEnsure = deckEnsure;

        when(snapshots.acquireBuildLock(eq(VIEWER), anyString()))
                .thenReturn(Uni.createFrom().item(true));
        when(snapshots.releaseBuildLock(eq(VIEWER), anyString()))
                .thenReturn(Uni.createFrom().voidItem());
        when(snapshots.load(VIEWER)).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(true));
    }

    @Test
    @DisplayName("Scenario: Given a successful fresh build, when source ordering is imported, then fresh cards are installed and repeat is not consulted")
    void successfulBuildInstallsFreshCardsBeforeAnyRepeatFallback() {
        // Given
        givenRefreshStartedNow();
        when(deckEnsure.ensure(VIEWER)).thenReturn(Uni.createFrom().item(true));
        when(sourceRedis.readStable(VIEWER, DeckSnapshotBuilder.MAX_FRESH))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(List.of(CANDIDATE), "42")));
        when(profiles.cards(List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Map.of(CANDIDATE, card(CANDIDATE))));
        when(viewerMutations.swiped(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(viewerMutations.matched(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.READY), eq("42"), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.READY), eq("42"), any(Instant.class));
        verify(viewerMutations, never()).repeatCandidates(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Scenario: Given a successful empty source after 30 seconds, when recovery is evaluated, then an EMPTY snapshot is installed without recording a failure")
    void successfulEmptySourceBecomesEmptyWithoutFailure() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        String sourceTimestamp = Long.toString(Instant.now().toEpochMilli());
        givenRefreshStartedAt(refreshStartedAt);
        when(deckEnsure.ensure(VIEWER)).thenReturn(Uni.createFrom().item(false));
        when(sourceRedis.readStable(VIEWER, DeckSnapshotBuilder.MAX_FRESH))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(List.of(), sourceTimestamp)));
        when(profiles.cards(List.of())).thenReturn(Uni.createFrom().item(Map.of()));
        when(viewerMutations.swiped(VIEWER, List.of()))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(viewerMutations.matched(VIEWER, List.of()))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of()),
                eq(DeckState.EMPTY), eq(sourceTimestamp), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of()),
                eq(DeckState.EMPTY), eq(sourceTimestamp), any(Instant.class));
        verify(snapshots, never()).recordFailure(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
        verify(sourceRedis).readStable(VIEWER, DeckSnapshotBuilder.MAX_FRESH);
    }

    @Test
    @DisplayName("Scenario: Given a successful empty source before 30 seconds, when recovery is evaluated, then the deck remains BUILDING without recording a failure")
    void successfulEmptySourceWaitsForBuildWindow() {
        // Given
        givenRefreshStartedNow();
        when(deckEnsure.ensure(VIEWER)).thenReturn(Uni.createFrom().item(false));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots, never()).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of()),
                eq(DeckState.EMPTY), eq(""), any(Instant.class));
        verify(snapshots, never()).recordFailure(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
        verify(viewerMutations, never()).repeatCandidates(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Scenario: Given ensure returns false because another Deck build completed, when its source timestamp is visible, then fresh ordering is imported instead of false EMPTY")
    void falseEnsureImportsSourceProducedByConcurrentBuild() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        String sourceTimestamp = Long.toString(Instant.now().toEpochMilli());
        givenRefreshStartedAt(refreshStartedAt);
        when(deckEnsure.ensure(VIEWER)).thenReturn(Uni.createFrom().item(false));
        when(sourceRedis.readStable(VIEWER, DeckSnapshotBuilder.MAX_FRESH))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(
                        List.of(CANDIDATE), sourceTimestamp)));
        when(profiles.cards(List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Map.of(CANDIDATE, card(CANDIDATE))));
        when(viewerMutations.swiped(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(viewerMutations.matched(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.READY), eq(sourceTimestamp), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.READY), eq(sourceTimestamp), any(Instant.class));
        verify(snapshots, never()).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of()),
                eq(DeckState.EMPTY), eq(""), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given ensure returns false with only an older source timestamp, when recovery runs, then the source is not accepted as EMPTY")
    void falseEnsureDoesNotAcceptSourceFromBeforeRefresh() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        String staleSourceTimestamp = Long.toString(refreshStartedAt.minusSeconds(30).toEpochMilli());
        givenRefreshStartedAt(refreshStartedAt);
        when(deckEnsure.ensure(VIEWER)).thenReturn(Uni.createFrom().item(false));
        when(sourceRedis.readStable(VIEWER, DeckSnapshotBuilder.MAX_FRESH))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(
                        List.of(), staleSourceTimestamp)));
        when(snapshots.recordFailure(eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1));
        when(viewerMutations.repeatCandidates(
                eq(VIEWER), eq(DeckSnapshotBuilder.MAX_REPEAT), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(snapshots.markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots, never()).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of()),
                eq(DeckState.EMPTY), anyString(), any(Instant.class));
        verify(snapshots).recordFailure(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
        verify(snapshots).markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given the first build failure before 30 seconds, when recovery is evaluated, then repeat cards remain disabled")
    void firstFailureBeforeDelayDoesNotEnableRepeat() {
        // Given
        givenRefreshStartedNow();
        givenBuildFailure(1);

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(viewerMutations, never()).repeatCandidates(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Scenario: Given two build failures, when recovery is evaluated before 30 seconds, then an eligible stale card is installed as repeat")
    void secondFailureEnablesRepeatBeforeDelay() {
        // Given
        givenRefreshStartedNow();
        givenBuildFailure(2);
        givenEligibleRepeatCard();

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of(CANDIDATE)),
                eq(DeckState.DEGRADED), eq(""), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given one build failure lasting more than 30 seconds, when recovery is evaluated, then an eligible stale card is installed as repeat")
    void thirtySecondDelayEnablesRepeatAfterOneFailure() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        givenRefreshStartedAt(refreshStartedAt);
        givenBuildFailure(1);
        givenEligibleRepeatCard();

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of(CANDIDATE)),
                eq(DeckState.DEGRADED), eq(""), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given fresh recovery is ready but repeat history is not, when fallback is allowed, then repeat remains disabled and the deck becomes unavailable")
    void repeatFallbackWaitsForItsIndependentRecoveryMarker() {
        // Given
        givenRefreshStartedNow();
        givenBuildFailure(2);
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(false));
        when(snapshots.markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(viewerMutations, never()).repeatCandidates(any(), anyInt(), any());
        verify(snapshots).markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given the initial build fails twice without repeat cards, when recovery completes, then unavailable metadata is installed")
    void initialFailureWithoutRepeatCardsBecomesUnavailable() {
        // Given
        givenRefreshStartedNow();
        givenBuildFailure(2);
        when(viewerMutations.repeatCandidates(eq(VIEWER), eq(DeckSnapshotBuilder.MAX_REPEAT), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(profiles.cards(List.of())).thenReturn(Uni.createFrom().item(Map.of()));
        when(viewerMutations.matched(VIEWER, List.of())).thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).markUnavailable(eq(VIEWER), anyString(), eq(0L), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given an existing fresh snapshot and eligible repeat, when refresh fallback is installed, then fresh survives and remains before repeat")
    void refreshFallbackPreservesFreshBeforeRepeat() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        DeckSnapshot existing = new DeckSnapshot(
                new DeckSnapshotMeta(3, Instant.now().minusSeconds(3_700), DeckState.READY,
                        "source-3", refreshStartedAt, 1, false),
                List.of(CANDIDATE),
                List.of());
        when(snapshots.load(VIEWER)).thenReturn(Uni.createFrom().item(Optional.of(existing)));
        when(snapshots.markRefreshRequested(eq(VIEWER), anyString(), eq(3L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(Optional.of(existing.meta())));
        when(deckEnsure.ensure(VIEWER))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Deck ensure failed")));
        when(snapshots.recordFailure(eq(VIEWER), anyString(), eq(3L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(2));
        when(viewerMutations.repeatCandidates(eq(VIEWER), eq(DeckSnapshotBuilder.MAX_REPEAT), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of(REPEAT_CANDIDATE)));
        when(profiles.cards(List.of(CANDIDATE, REPEAT_CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Map.of(
                        CANDIDATE, card(CANDIDATE),
                        REPEAT_CANDIDATE, card(REPEAT_CANDIDATE))));
        when(viewerMutations.swiped(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(viewerMutations.matched(VIEWER, List.of(CANDIDATE, REPEAT_CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(3L), anyString(), eq(List.of(CANDIDATE)), eq(List.of(REPEAT_CANDIDATE)),
                eq(DeckState.DEGRADED), eq("source-3"), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(4L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(3L), anyString(), eq(List.of(CANDIDATE)), eq(List.of(REPEAT_CANDIDATE)),
                eq(DeckState.DEGRADED), eq("source-3"), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario: Given an existing fresh snapshot and no repeat candidates, when refresh fallback is installed, then eligible fresh cards remain available")
    void refreshFallbackKeepsFreshWhenRepeatIsEmpty() {
        // Given
        Instant refreshStartedAt = Instant.now().minusSeconds(31);
        DeckSnapshot existing = new DeckSnapshot(
                new DeckSnapshotMeta(3, Instant.now().minusSeconds(3_700), DeckState.READY,
                        "source-3", refreshStartedAt, 1, false),
                List.of(CANDIDATE),
                List.of());
        when(snapshots.load(VIEWER)).thenReturn(Uni.createFrom().item(Optional.of(existing)));
        when(snapshots.markRefreshRequested(eq(VIEWER), anyString(), eq(3L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(Optional.of(existing.meta())));
        when(deckEnsure.ensure(VIEWER))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Deck ensure failed")));
        when(snapshots.recordFailure(eq(VIEWER), anyString(), eq(3L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(2));
        when(viewerMutations.repeatCandidates(
                eq(VIEWER), eq(DeckSnapshotBuilder.MAX_REPEAT), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(profiles.cards(List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Map.of(CANDIDATE, card(CANDIDATE))));
        when(viewerMutations.swiped(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(viewerMutations.matched(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(3L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.DEGRADED), eq("source-3"), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(4L));

        // When
        builder.build(VIEWER).await().indefinitely();

        // Then
        verify(snapshots).install(
                eq(VIEWER), eq(3L), anyString(), eq(List.of(CANDIDATE)), eq(List.of()),
                eq(DeckState.DEGRADED), eq("source-3"), any(Instant.class));
        verify(snapshots, never()).markUnavailable(
                eq(VIEWER), anyString(), eq(3L), any(Instant.class));
    }

    private void givenRefreshStartedNow() {
        givenRefreshStartedAt(Instant.now());
    }

    private void givenRefreshStartedAt(Instant refreshStartedAt) {
        when(snapshots.markRefreshRequested(
                eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(Optional.of(meta(refreshStartedAt, 0))));
    }

    private void givenBuildFailure(int failureCount) {
        when(deckEnsure.ensure(VIEWER))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Deck ensure failed")));
        when(snapshots.recordFailure(eq(VIEWER), anyString(), eq(0L), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(failureCount));
    }

    private void givenEligibleRepeatCard() {
        when(viewerMutations.repeatCandidates(eq(VIEWER), eq(DeckSnapshotBuilder.MAX_REPEAT), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of(CANDIDATE)));
        when(profiles.cards(List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Map.of(CANDIDATE, card(CANDIDATE))));
        when(viewerMutations.matched(VIEWER, List.of(CANDIDATE)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(snapshots.install(
                eq(VIEWER), eq(0L), anyString(), eq(List.of()), eq(List.of(CANDIDATE)),
                eq(DeckState.DEGRADED), eq(""), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));
    }

    private DeckSnapshotMeta meta(Instant refreshStartedAt, int failureCount) {
        return new DeckSnapshotMeta(
                0, null, DeckState.REFRESHING, "", refreshStartedAt, failureCount, false);
    }

    private DeckCardDto card(UUID profileId) {
        return new DeckCardDto(
                profileId, "Stale candidate", 29, "Vienna", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
    }
}
