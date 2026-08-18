package com.tinder.deckread.service;

import com.tinder.deckread.messaging.DeckMaterializationRequester;
import com.tinder.deckread.messaging.MaterializationReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Feature: Deck refresh requests remain best effort and observable")
class DeckRefreshTriggerTest {

    @Test
    @DisplayName("Scenario: Given asynchronous publication failure, when refresh is requested, then HTTP flow is not failed and failure is counted")
    void asynchronousFailureIsContainedAndCounted() {
        UUID viewer = UUID.randomUUID();
        DeckMaterializationRequester requester = mock(DeckMaterializationRequester.class);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        when(requester.request(viewer, MaterializationReason.API_STALE))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("Kafka unavailable")));
        DeckRefreshTrigger trigger = new DeckRefreshTrigger(requester, meters);

        assertThatCode(() -> trigger.request(viewer, MaterializationReason.API_STALE))
                .doesNotThrowAnyException();

        assertThat(meters.counter("deck_read_materialization_requests", "outcome", "failed").count())
                .isEqualTo(1);
    }
}
