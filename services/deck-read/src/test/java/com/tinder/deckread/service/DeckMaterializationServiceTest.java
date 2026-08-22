package com.tinder.deckread.service;

import com.tinder.deckread.client.DeckEnsureClient;
import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.messaging.DeckMaterializationRequest;
import com.tinder.deckread.messaging.MaterializationReason;
import com.tinder.deckread.readmodel.DeckMaterializationRequestStore;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckMeta;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.deckread.redis.DeckRedisReader;
import com.tinder.deckread.redis.SourceDeckSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("acceptance")
@DisplayName("Feature: materialization queue records are revision-idempotent")
class DeckMaterializationServiceTest {

    @Test
    void staleQueueRecordIsSkippedBeforeLockOrDownstreamWork() {
        UUID viewer = UUID.randomUUID();
        DeckMaterializationRequestStore requests = mock(DeckMaterializationRequestStore.class);
        DeckSnapshotStore locks = mock(DeckSnapshotStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        when(requests.requestedRevision(viewer)).thenReturn(Uni.createFrom().item(2L));
        DeckMaterializationService service = service(requests, locks, materialized);

        service.materialize(request(viewer, 1)).await().indefinitely();

        verifyNoInteractions(locks, materialized);
    }

    @Test
    void duplicatePublishedRevisionIsSkippedBeforeLockOrDownstreamWork() {
        UUID viewer = UUID.randomUUID();
        DeckMaterializationRequestStore requests = mock(DeckMaterializationRequestStore.class);
        DeckSnapshotStore locks = mock(DeckSnapshotStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        when(requests.requestedRevision(viewer)).thenReturn(Uni.createFrom().item(3L));
        when(materialized.meta(viewer)).thenReturn(Uni.createFrom().item(Optional.of(
                new MaterializedDeckMeta(
                        8, 3, 3, Instant.now(), com.tinder.deckread.dto.DeckState.READY,
                        "source", 100, 120, false, 100))));
        DeckMaterializationService service = service(requests, locks, materialized);

        service.materialize(request(viewer, 3)).await().indefinitely();

        verifyNoInteractions(locks);
    }

    @Test
    @DisplayName("Scenario: Given unseen and eligible repeats, when the worker installs, then unseen cards precede repeats")
    void successfulMaterializationAppendsRepeatsAfterUnseen() {
        UUID viewer = UUID.randomUUID();
        UUID unseenId = UUID.randomUUID();
        UUID repeatId = UUID.randomUUID();
        DeckCardDto unseen = card(unseenId);
        DeckCardDto repeat = card(repeatId);
        DeckMaterializationRequestStore requests = mock(DeckMaterializationRequestStore.class);
        DeckSnapshotStore locks = mock(DeckSnapshotStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        DeckEnsureClient deckEnsure = mock(DeckEnsureClient.class);
        DeckRedisReader source = mock(DeckRedisReader.class);
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);
        when(requests.requestedRevision(viewer)).thenReturn(Uni.createFrom().item(1L));
        when(materialized.meta(viewer)).thenReturn(Uni.createFrom().item(Optional.empty()));
        when(locks.acquireBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().item(true));
        when(locks.renewBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().item(true));
        when(locks.releaseBuildLock(eq(viewer), anyString())).thenReturn(Uni.createFrom().voidItem());
        when(deckEnsure.ensure(viewer)).thenReturn(Uni.createFrom().item(true));
        when(source.readStable(viewer, MaterializedDeckStore.TOTAL_WINDOW))
                .thenReturn(Uni.createFrom().item(new SourceDeckSnapshot(List.of(unseenId), "100")));
        when(profiles.cards(List.of(unseenId)))
                .thenReturn(Uni.createFrom().item(Map.of(unseenId, unseen)));
        when(mutations.swiped(viewer, List.of(unseenId))).thenReturn(Uni.createFrom().item(Set.of()));
        when(mutations.matched(viewer, List.of(unseenId))).thenReturn(Uni.createFrom().item(Set.of()));
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(true));
        when(mutations.repeatCandidates(eq(viewer), anyInt(), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of(repeatId)));
        when(profiles.cards(List.of(repeatId)))
                .thenReturn(Uni.createFrom().item(Map.of(repeatId, repeat)));
        when(mutations.matched(viewer, List.of(repeatId))).thenReturn(Uni.createFrom().item(Set.of()));
        when(materialized.install(
                eq(viewer), eq(1L), eq(List.of(unseen, repeat)), eq(1),
                eq(DeckState.READY), eq("100"), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(1L));

        DeckMaterializationService service = service(requests, locks, materialized);
        service.deckEnsure = deckEnsure;
        service.source = source;
        service.profiles = profiles;
        service.mutations = mutations;
        service.readiness = readiness;

        service.materialize(request(viewer, 1)).await().indefinitely();

        verify(materialized).install(
                eq(viewer), eq(1L), eq(List.of(unseen, repeat)), eq(1),
                eq(DeckState.READY), eq("100"), any(Instant.class));
    }

    private DeckMaterializationService service(
            DeckMaterializationRequestStore requests,
            DeckSnapshotStore locks,
            MaterializedDeckStore materialized
    ) {
        DeckMaterializationService service = new DeckMaterializationService(new SimpleMeterRegistry());
        service.requests = requests;
        service.locks = locks;
        service.materialized = materialized;
        return service;
    }

    private DeckMaterializationRequest request(UUID viewer, long revision) {
        return new DeckMaterializationRequest(
                UUID.randomUUID(), viewer, revision, MaterializationReason.API_MISS, "", Instant.now());
    }

    private DeckCardDto card(UUID id) {
        return new DeckCardDto(
                id, "candidate", 28, "Vienna", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
    }
}
