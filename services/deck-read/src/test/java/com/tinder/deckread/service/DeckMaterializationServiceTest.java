package com.tinder.deckread.service;

import com.tinder.deckread.messaging.DeckMaterializationRequest;
import com.tinder.deckread.messaging.MaterializationReason;
import com.tinder.deckread.readmodel.DeckMaterializationRequestStore;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckMeta;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
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
                        "source", 100, 120, false))));
        DeckMaterializationService service = service(requests, locks, materialized);

        service.materialize(request(viewer, 3)).await().indefinitely();

        verifyNoInteractions(locks);
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
}
