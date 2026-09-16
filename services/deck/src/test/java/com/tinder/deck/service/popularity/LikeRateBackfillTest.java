package com.tinder.deck.service.popularity;

import com.tinder.deck.adapters.ProfileSwipeCountsHttp;
import com.tinder.deck.adapters.ProfileSwipeCountsHttp.ProfileSwipeCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LikeRateBackfill.
 *
 * <p>A rebuild is only ever run after something went wrong, so the properties that matter are
 * that it starts from a known state and that one bad row cannot abort the whole recovery.
 */
@ExtendWith(MockitoExtension.class)
class LikeRateBackfillTest {

    @Mock
    private ProfileSwipeCountsHttp counts;

    @Mock
    private LikeRateStore store;

    @Test
    void clearsBeforeWritingSoNothingIsDoubleCounted() {
        UUID profile = UUID.randomUUID();
        when(store.clear()).thenReturn(Mono.empty());
        when(counts.all()).thenReturn(Flux.just(new ProfileSwipeCount(profile.toString(), 10, 3)));
        when(store.restore(any(), anyLong(), anyLong())).thenReturn(Mono.empty());
        when(store.restoreTotals(anyLong(), anyLong())).thenReturn(Mono.empty());

        new LikeRateBackfill(counts, store).run().block();

        // Merging into a surviving partial state would count those impressions twice, and a
        // counter wrong in an unknown direction is worse than one briefly empty.
        var order = inOrder(store);
        order.verify(store).clear();
        order.verify(store).restore(eq(profile), eq(10L), eq(3L));
    }

    @Test
    void sumsWhatItRestoredIntoTheRunningTotals() {
        when(store.clear()).thenReturn(Mono.empty());
        when(counts.all()).thenReturn(Flux.just(
                new ProfileSwipeCount(UUID.randomUUID().toString(), 10, 3),
                new ProfileSwipeCount(UUID.randomUUID().toString(), 6, 1)));
        when(store.restore(any(), anyLong(), anyLong())).thenReturn(Mono.empty());
        when(store.restoreTotals(anyLong(), anyLong())).thenReturn(Mono.empty());

        LikeRateBackfill.Result result = new LikeRateBackfill(counts, store).run().block();

        assertThat(result).isNotNull();
        assertThat(result.profiles()).isEqualTo(2);
        assertThat(result.impressions()).isEqualTo(16);
        assertThat(result.likes()).isEqualTo(4);
        verify(store).restoreTotals(16L, 4L);
    }

    @Test
    void skipsAnUnreadableProfileIdWithoutAbandoningTheRebuild() {
        UUID good = UUID.randomUUID();
        when(store.clear()).thenReturn(Mono.empty());
        when(counts.all()).thenReturn(Flux.just(
                new ProfileSwipeCount("not-a-uuid", 5, 5),
                new ProfileSwipeCount(good.toString(), 4, 2)));
        when(store.restore(any(), anyLong(), anyLong())).thenReturn(Mono.empty());
        when(store.restoreTotals(anyLong(), anyLong())).thenReturn(Mono.empty());

        LikeRateBackfill.Result result = new LikeRateBackfill(counts, store).run().block();

        // One malformed row must not cost the operator the entire recovery.
        assertThat(result).isNotNull();
        assertThat(result.profiles()).isEqualTo(1);
        verify(store).restore(eq(good), eq(4L), eq(2L));
        verify(store, never()).restore(any(), eq(5L), eq(5L));
    }

    @Test
    void reportsAnEmptyHistoryAsAnEmptyRebuild() {
        when(store.clear()).thenReturn(Mono.empty());
        when(counts.all()).thenReturn(Flux.empty());
        when(store.restoreTotals(anyLong(), anyLong())).thenReturn(Mono.empty());

        LikeRateBackfill.Result result = new LikeRateBackfill(counts, store).run().block();

        assertThat(result).isNotNull();
        assertThat(result.profiles()).isZero();
        verify(store).restoreTotals(0L, 0L);
    }
}
