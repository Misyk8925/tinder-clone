package com.tinder.deck.service;

import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.contracts.dto.SharedPreferencesDto;
import com.tinder.deck.adapters.ProfilesHttp;
import com.tinder.deck.kafka.producer.DeckBuiltEventPublisher;
import com.tinder.deck.service.pipeline.DeckPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeckBuiltEventPublishingTest {

    @Test
    void publishesOnlyAfterStableSnapshotCanBeRead() {
        ProfilesHttp profiles = mock(ProfilesHttp.class);
        DeckCache cache = mock(DeckCache.class);
        DeckPipeline pipeline = mock(DeckPipeline.class);
        DeckBuiltEventPublisher publisher = mock(DeckBuiltEventPublisher.class);
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = new SharedProfileDto(
                viewerId, "viewer", 28, "bio", "Vienna", true, null,
                new SharedPreferencesDto(18, 99, "ANY", 100), false, List.of(), List.of());
        when(pipeline.buildDeck(viewer)).thenReturn(Mono.empty());
        when(cache.getBuildInstant(viewerId))
                .thenReturn(Mono.just(Optional.of(Instant.ofEpochMilli(1234))));
        when(cache.size(viewerId)).thenReturn(Mono.just(0L));
        when(publisher.publish(viewerId, "1234", 0)).thenReturn(Mono.empty());
        DeckService service = new DeckService(profiles, cache, pipeline);
        ReflectionTestUtils.setField(service, "deckBuiltEvents", publisher);

        StepVerifier.create(service.rebuildOneDeck(viewer)).verifyComplete();

        verify(publisher).publish(viewerId, "1234", 0);
    }

    @Test
    void publisherFailureDoesNotInvalidateTheStableRedisSnapshot() {
        ProfilesHttp profiles = mock(ProfilesHttp.class);
        DeckCache cache = mock(DeckCache.class);
        DeckPipeline pipeline = mock(DeckPipeline.class);
        DeckBuiltEventPublisher publisher = mock(DeckBuiltEventPublisher.class);
        UUID viewerId = UUID.randomUUID();
        SharedProfileDto viewer = new SharedProfileDto(
                viewerId, "viewer", 28, "bio", "Vienna", true, null,
                new SharedPreferencesDto(18, 99, "ANY", 100), false, List.of(), List.of());
        when(pipeline.buildDeck(viewer)).thenReturn(Mono.empty());
        when(cache.getBuildInstant(viewerId))
                .thenReturn(Mono.just(Optional.of(Instant.ofEpochMilli(1234))));
        when(cache.size(viewerId)).thenReturn(Mono.just(1L));
        when(publisher.publish(viewerId, "1234", 1))
                .thenReturn(Mono.error(new IllegalStateException("kafka unavailable")));
        DeckService service = new DeckService(profiles, cache, pipeline);
        ReflectionTestUtils.setField(service, "deckBuiltEvents", publisher);

        StepVerifier.create(service.rebuildOneDeck(viewer)).verifyComplete();

        verify(publisher).publish(viewerId, "1234", 1);
    }
}
