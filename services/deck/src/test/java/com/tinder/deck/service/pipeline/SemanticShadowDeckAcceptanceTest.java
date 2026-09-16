package com.tinder.deck.service.pipeline;

import com.tinder.contracts.dto.SharedProfileDto;
import com.tinder.deck.semantic.ShadowRunner;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outside-in acceptance for FR-3 and the provider-failure path. */
class SemanticShadowDeckAcceptanceTest {

    @Test
    void givenShadowRunnerFailsWhenDeckBuildsThenBaselineStillPublishes() {
        UUID viewerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        SharedProfileDto viewer = mock(SharedProfileDto.class);
        SharedProfileDto candidate = mock(SharedProfileDto.class);
        when(viewer.id()).thenReturn(viewerId);
        when(candidate.id()).thenReturn(candidateId);

        CandidateSearchStage search = mock(CandidateSearchStage.class);
        SwipeFilterStage swipes = mock(SwipeFilterStage.class);
        ScoringStage scoring = mock(ScoringStage.class);
        CacheStage cache = mock(CacheStage.class);
        ShadowRunner shadow = mock(ShadowRunner.class);
        ScoringStage.ScoredCandidate ranked = new ScoringStage.ScoredCandidate(candidateId, 0.8);

        when(search.searchCandidates(viewer)).thenReturn(Flux.just(candidate));
        when(swipes.filterBySwipeHistory(eq(viewer), any())).thenReturn(Flux.just(candidate));
        when(scoring.scoreAndRank(eq(viewer), any())).thenReturn(Flux.just(ranked));
        when(cache.cacheDeck(eq(viewerId), any())).thenReturn(Mono.empty());
        doThrow(new IllegalStateException("synthetic provider failed"))
                .when(shadow).submit(eq(viewer), eq(List.of(ranked)));

        DeckPipeline pipeline = new DeckPipeline(search, swipes, scoring, cache, shadow);
        ReflectionTestUtils.setField(pipeline, "perUserLimit", 500);

        StepVerifier.create(pipeline.buildDeck(viewer)).verifyComplete();
        verify(cache).cacheDeck(eq(viewerId), any());
        verify(shadow).submit(viewer, List.of(ranked));
    }
}
