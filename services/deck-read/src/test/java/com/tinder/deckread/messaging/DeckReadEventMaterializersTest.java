package com.tinder.deckread.messaging;

import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
import com.tinder.contracts.event.v1.ProfileDeckCardProjectionEvent;
import com.tinder.contracts.event.v1.ProfileProjectionOperation;
import com.tinder.contracts.event.v1.DeckCardPreferences;
import com.tinder.contracts.event.v1.DeckCardProjection;
import com.tinder.contracts.event.v1.ProjectionSource;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@Tag("acceptance")
@DisplayName("Feature: Kafka projection materializers retry bounded transient Redis failures")
class DeckReadEventMaterializersTest {

    private ViewerMutationStore mutations;
    private DeckReadEventMaterializers materializers;

    @BeforeEach
    void setUp() {
        mutations = mock(ViewerMutationStore.class);
        materializers = new DeckReadEventMaterializers();
        materializers.profiles = mock(ProfileProjectionStore.class);
        materializers.viewerMutations = mutations;
        materializers.requester = mock(DeckMaterializationRequester.class);
        materializers.hotViewers = mock(com.tinder.deckread.readmodel.HotViewerIndex.class);
        materializers.materialization = mock(com.tinder.deckread.service.DeckMaterializationService.class);
        when(materializers.requester.request(
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.any(MaterializationReason.class)))
                .thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    @DisplayName("Scenario: Given two transient Redis failures, when a swipe is materialized, then the third attempt succeeds")
    void retriesTransientFailureBeforeAcknowledgement() {
        // Given
        SwipeSavedEvent event = swipe();
        when(mutations.applySwipe(event))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("redis-1")))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("redis-2")))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        materializers.onSwipeSaved(event).await().indefinitely();

        // Then
        verify(mutations, times(3)).applySwipe(event);
    }

    @Test
    @DisplayName("Scenario: Given persistent Redis failure, when the retry budget is exhausted, then failure is propagated to the connector")
    void propagatesFailureAfterBoundedAttempts() {
        // Given
        SwipeSavedEvent event = swipe();
        when(mutations.applySwipe(event))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("redis-down")));

        // When / Then
        assertThatThrownBy(() -> materializers.onSwipeSaved(event).await().indefinitely())
                .hasMessage("redis-down");
        verify(mutations, times(4)).applySwipe(event);
    }

    @Test
    @DisplayName("Scenario: Given one failed profile fan-out target, when the event is handled, then remaining viewers are still enqueued before DLT failure")
    void profileFanOutContinuesAfterOneViewerFails() {
        UUID profile = UUID.randomUUID();
        UUID failedViewer = UUID.randomUUID();
        UUID healthyViewer = UUID.randomUUID();
        ProfileDeckCardProjectionEvent event = new ProfileDeckCardProjectionEvent(
                UUID.randomUUID(), profile, UUID.randomUUID().toString(), 1, Instant.now(),
                ProfileProjectionOperation.UPSERT, ProjectionSource.LIVE, null,
                new DeckCardProjection(
                        profile, "profile", 28, "Vienna", "bio", true,
                        new DeckCardPreferences(18, 99, "ANY", 100),
                        java.util.List.of(), java.util.List.of()));
        when(materializers.profiles.apply(event)).thenReturn(Uni.createFrom().voidItem());
        when(materializers.hotViewers.viewers(profile))
                .thenReturn(Uni.createFrom().item(java.util.Set.of(failedViewer, healthyViewer)));
        when(materializers.requester.request(any(UUID.class), eq(MaterializationReason.PROFILE_CHANGED)))
                .thenAnswer(invocation -> invocation.<UUID>getArgument(0).equals(failedViewer)
                        ? Uni.createFrom().failure(new IllegalStateException("kafka unavailable"))
                        : Uni.createFrom().voidItem());

        assertThatThrownBy(() -> materializers.onProfileDeckCardProjection(event).await().indefinitely())
                .hasMessage("Profile materialization fan-out was only partially enqueued");

        verify(materializers.requester).request(healthyViewer, MaterializationReason.PROFILE_CHANGED);
        verify(materializers.requester).request(profile, MaterializationReason.PROFILE_CHANGED);
    }

    private SwipeSavedEvent swipe() {
        return new SwipeSavedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), false, Instant.now().toEpochMilli());
    }
}
