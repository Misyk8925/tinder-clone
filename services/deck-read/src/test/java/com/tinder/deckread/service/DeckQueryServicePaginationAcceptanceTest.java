package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckPage;
import com.tinder.deckread.dto.DeckState;
import com.tinder.deckread.readmodel.DeckSnapshot;
import com.tinder.deckread.readmodel.DeckSnapshotMeta;
import com.tinder.deckread.readmodel.DeckSnapshotStore;
import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ReadModelReadiness;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@Tag("acceptance")
@DisplayName("Feature: Deck v2 cursor addresses the immutable snapshot order")
class DeckQueryServicePaginationAcceptanceTest {

    private static final String VIEWER_USER_ID = "viewer-user";
    private static final UUID VIEWER_PROFILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ProfileProjectionStore profiles;
    private DeckSnapshotStore snapshots;
    private ViewerMutationStore mutations;
    private DeckSnapshotBuilder builder;
    private DeckQueryService service;
    private List<UUID> ordered;
    private AtomicReference<Set<UUID>> swiped;

    @BeforeEach
    void setUp() {
        profiles = mock(ProfileProjectionStore.class);
        snapshots = mock(DeckSnapshotStore.class);
        mutations = mock(ViewerMutationStore.class);
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);
        builder = mock(DeckSnapshotBuilder.class);

        DeckCursorCodec cursors = new DeckCursorCodec();
        cursors.secret = "acceptance-test-secret";

        service = new DeckQueryService();
        service.profiles = profiles;
        service.snapshots = snapshots;
        service.viewerMutations = mutations;
        service.readiness = readiness;
        service.builder = builder;
        service.cursors = cursors;
        service.materializedQuery = mock(MaterializedDeckQuery.class);
        service.refreshes = mock(DeckRefreshTrigger.class);
        when(service.materializedQuery.getV2(
                eq(VIEWER_PROFILE_ID), anyLong(), anyInt(), anyInt()))
                .thenReturn(Uni.createFrom().item(Optional.empty()));

        ordered = java.util.stream.IntStream.range(0, 250)
                .mapToObj(index -> UUID.nameUUIDFromBytes(("candidate-" + index).getBytes()))
                .toList();
        DeckSnapshot snapshot = new DeckSnapshot(
                new DeckSnapshotMeta(7, Instant.now(), DeckState.READY, "source-7", null, 0, false),
                ordered,
                List.of());

        when(readiness.isReady()).thenReturn(Uni.createFrom().item(true));
        when(profiles.viewerProfileId(VIEWER_USER_ID)).thenReturn(Uni.createFrom().item(VIEWER_PROFILE_ID));
        when(snapshots.load(VIEWER_PROFILE_ID)).thenReturn(Uni.createFrom().item(java.util.Optional.of(snapshot)));
        swiped = new AtomicReference<>(Set.of());
        when(mutations.swiped(eq(VIEWER_PROFILE_ID), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> candidates = (List<UUID>) invocation.getArgument(1);
            return Uni.createFrom().item(candidates.stream().filter(swiped.get()::contains).collect(java.util.stream.Collectors.toSet()));
        });
        when(mutations.matched(eq(VIEWER_PROFILE_ID), any())).thenReturn(Uni.createFrom().item(Set.of()));
        when(profiles.cards(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<UUID> ids = (List<UUID>) invocation.getArgument(0);
            Map<UUID, DeckCardDto> cards = new LinkedHashMap<>();
            ids.forEach(id -> cards.put(id, card(id)));
            return Uni.createFrom().item(Map.copyOf(cards));
        });
    }

    @Test
    @DisplayName("Scenario: Given cards before a cursor are swiped, when the next page is read, then unseen snapshot positions are not skipped")
    void viewerMutationsBeforeCursorDoNotSkipUnseenCards() {
        // Given
        DeckPage firstPage = page(service.getDeckV2(VIEWER_USER_ID, null, 20).await().indefinitely());
        swiped.set(Set.copyOf(ordered.subList(0, 17)));

        // When
        DeckPage secondPage = page(service.getDeckV2(
                VIEWER_USER_ID, firstPage.nextCursor(), 20).await().indefinitely());

        // Then
        assertThat(secondPage.items()).extracting(DeckCardDto::profileId)
                .containsExactlyElementsOf(ordered.subList(20, 40));
    }

    @Test
    @DisplayName("Scenario: Given a 250-card snapshot, when a 100-card page is read, then projection hydration is bounded to that page window")
    void hydrationDoesNotFanOutAcrossTheWholeSnapshot() {
        // Given a 250-card snapshot
        // When
        DeckPage page = page(service.getDeckV2(VIEWER_USER_ID, null, 100).await().indefinitely());

        // Then
        assertThat(page.items()).hasSize(100);
        verify(profiles).cards(ordered.subList(0, 100));
    }

    @Test
    @DisplayName("Scenario: Given an unavailable snapshot, when the client polls v2, then recovery is retried while the current response remains 503")
    void unavailableSnapshotPollRequestsAnotherBuild() {
        // Given
        DeckSnapshot unavailable = new DeckSnapshot(
                new DeckSnapshotMeta(8, Instant.now(), DeckState.DEGRADED, "", null, 2, true),
                List.of(),
                List.of());
        when(snapshots.load(VIEWER_PROFILE_ID))
                .thenReturn(Uni.createFrom().item(java.util.Optional.of(unavailable)));

        // When
        DeckQueryResult result = service.getDeckV2(VIEWER_USER_ID, null, 20).await().indefinitely();

        // Then
        assertThat(result).isInstanceOf(DeckQueryResult.Failure.class);
        assertThat(((DeckQueryResult.Failure) result).code())
                .isEqualTo(DeckQueryService.DECK_TEMPORARILY_UNAVAILABLE);
        verify(builder).requestBuild(VIEWER_PROFILE_ID);
    }

    private DeckPage page(DeckQueryResult result) {
        assertThat(result).isInstanceOf(DeckQueryResult.Page.class);
        return ((DeckQueryResult.Page) result).value();
    }

    private DeckCardDto card(UUID profileId) {
        return new DeckCardDto(
                profileId, "candidate", 28, "Vienna", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
    }
}
