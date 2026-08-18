package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.MaterializedDeckSlice;
import com.tinder.deckread.readmodel.MaterializedDeckStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("acceptance")
@DisplayName("Feature: the warm Deck HTTP path reads a ready materialized page")
class DeckQueryServiceMaterializedAcceptanceTest {

    @Test
    @DisplayName("Scenario: Given a ready page, when v2 is read, then no source, snapshot or per-card projection read occurs")
    void warmReadUsesOnlyIdentityAndMaterializedPage() {
        UUID viewer = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);
        DeckSnapshotStore snapshots = mock(DeckSnapshotStore.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        DeckRefreshTrigger refreshes = mock(DeckRefreshTrigger.class);
        when(readiness.isReady()).thenReturn(Uni.createFrom().item(true));
        when(profiles.viewerProfileId("viewer-user")).thenReturn(Uni.createFrom().item(viewer));
        when(materialized.readPage(viewer, 0, 0, 20)).thenReturn(Uni.createFrom().item(
                new MaterializedDeckSlice(
                        List.of(card(candidate)), 7, false, 1, 1,
                        DeckState.READY, Instant.now(), "100", false)));

        DeckQueryService service = new DeckQueryService();
        service.profiles = profiles;
        service.materializedQuery = new MaterializedDeckQuery(
                materialized, profiles, mutations, cursors(), refreshes);
        service.readiness = readiness;
        service.snapshots = snapshots;
        service.viewerMutations = mutations;
        service.refreshes = refreshes;
        service.materializedRequired = true;
        service.cursors = cursors();

        DeckQueryResult result = service.getDeckV2("viewer-user", null, 20).await().indefinitely();

        assertThat(result).isInstanceOf(DeckQueryResult.Page.class);
        assertThat(((DeckQueryResult.Page) result).value().items())
                .extracting(DeckCardDto::profileId).containsExactly(candidate);
        verify(profiles, never()).cards(org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(snapshots, mutations, refreshes);
        verify(materialized).readPage(viewer, 0, 0, 20);
    }

    @Test
    @DisplayName("Scenario: Given a materialized tail, when v1 reads past 100, then compatibility hydration uses the tail without Deck")
    void v1DeepPaginationUsesMaterializedTail() {
        UUID viewer = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        MaterializedDeckStore materialized = mock(MaterializedDeckStore.class);
        DeckSnapshotStore snapshots = mock(DeckSnapshotStore.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        DeckRefreshTrigger refreshes = mock(DeckRefreshTrigger.class);
        when(profiles.viewerProfileId("viewer-user")).thenReturn(Uni.createFrom().item(viewer));
        when(materialized.readPage(viewer, 0, 100, 20)).thenReturn(Uni.createFrom().item(
                new MaterializedDeckSlice(
                        List.of(), 9, false, 100, 120,
                        DeckState.READY, Instant.now(), "100", false)));
        when(materialized.readTail(viewer, 9, 0, 20))
                .thenReturn(Uni.createFrom().item(List.of(candidate)));
        when(profiles.cards(List.of(candidate)))
                .thenReturn(Uni.createFrom().item(Map.of(candidate, card(candidate))));
        when(mutations.swiped(viewer, List.of(candidate)))
                .thenReturn(Uni.createFrom().item(Set.of()));
        when(mutations.matched(viewer, List.of(candidate)))
                .thenReturn(Uni.createFrom().item(Set.of()));

        DeckQueryService service = new DeckQueryService();
        service.profiles = profiles;
        service.materializedQuery = new MaterializedDeckQuery(
                materialized, profiles, mutations, cursors(), refreshes);
        service.snapshots = snapshots;
        service.viewerMutations = mutations;
        service.refreshes = refreshes;
        service.materializedRequired = true;

        var cards = service.getDeckV1("viewer-user", 100, 20).await().indefinitely();

        assertThat(cards).extracting(card -> card.profileId()).containsExactly(candidate);
        verifyNoInteractions(snapshots);
    }

    private DeckCursorCodec cursors() {
        DeckCursorCodec codec = new DeckCursorCodec();
        codec.secret = "materialized-query-test-secret";
        return codec;
    }

    private DeckCardDto card(UUID id) {
        return new DeckCardDto(
                id, "candidate", 28, "Vienna", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
    }
}
