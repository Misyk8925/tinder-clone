package com.tinder.deckread.service;

import com.tinder.deckread.dto.DeckCardDto;
import com.tinder.deckread.dto.DeckState;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("acceptance")
@DisplayName("Feature: successful rebuilds append eligible repeats after unseen cards")
class DeckRepeatFillTest {

    @Test
    @DisplayName("Scenario: Given unseen already in the deck, when repeats are filled, then duplicates and matches are dropped")
    void dropsDuplicatesAndMatches() {
        UUID viewer = UUID.randomUUID();
        UUID unseen = UUID.randomUUID();
        UUID repeat = UUID.randomUUID();
        UUID matched = UUID.randomUUID();
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(true));
        when(mutations.repeatCandidates(eq(viewer), eq(10), any(Instant.class)))
                .thenReturn(Uni.createFrom().item(List.of(unseen, repeat, matched)));
        when(profiles.cards(List.of(repeat, matched))).thenReturn(Uni.createFrom().item(Map.of(
                repeat, card(repeat),
                matched, card(matched))));
        when(mutations.matched(viewer, List.of(repeat, matched)))
                .thenReturn(Uni.createFrom().item(Set.of(matched)));

        DeckRepeatFill.Result result = DeckRepeatFill.eligible(
                readiness, mutations, profiles, viewer, List.of(unseen), 10, Instant.now())
                .await().indefinitely();

        assertThat(result.ids()).containsExactly(repeat);
        assertThat(DeckRepeatFill.state(true, true)).isEqualTo(DeckState.READY);
        assertThat(DeckRepeatFill.state(false, true)).isEqualTo(DeckState.DEGRADED);
        assertThat(DeckRepeatFill.state(false, false)).isEqualTo(DeckState.EMPTY);
    }

    @Test
    @DisplayName("Scenario: Given repeat history is not ready, when fills are requested, then no candidates are read")
    void waitsForRepeatReadiness() {
        ReadModelReadiness readiness = mock(ReadModelReadiness.class);
        ViewerMutationStore mutations = mock(ViewerMutationStore.class);
        ProfileProjectionStore profiles = mock(ProfileProjectionStore.class);
        when(readiness.isRepeatReady()).thenReturn(Uni.createFrom().item(false));

        DeckRepeatFill.Result result = DeckRepeatFill.eligible(
                readiness, mutations, profiles, UUID.randomUUID(), List.of(), 10, Instant.now())
                .await().indefinitely();

        assertThat(result.ids()).isEmpty();
        verify(mutations, never()).repeatCandidates(any(), anyInt(), any());
    }

    private DeckCardDto card(UUID id) {
        return new DeckCardDto(
                id, "candidate", 28, "Vienna", "bio", true,
                new DeckCardDto.Preferences(18, 99, "ALL", 50), List.of(), List.of());
    }
}
