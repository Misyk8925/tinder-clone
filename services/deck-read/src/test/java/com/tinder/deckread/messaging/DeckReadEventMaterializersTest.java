package com.tinder.deckread.messaging;

import com.tinder.deckread.readmodel.ProfileProjectionStore;
import com.tinder.deckread.readmodel.ViewerMutationStore;
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

    private SwipeSavedEvent swipe() {
        return new SwipeSavedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), false, Instant.now().toEpochMilli());
    }
}
