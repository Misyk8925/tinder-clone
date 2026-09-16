package com.tinder.deck.experiment;

import com.tinder.deck.service.popularity.LikeRateBackfill;
import com.tinder.deck.service.popularity.LikeRateStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Operator view of the popularity counters.
 *
 * <p>Sits under {@code /api/v1/admin}, which the gateway already guards with the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/popularity")
public class PopularityAdminController {

    private final LikeRateStore store;
    private final LikeRateBackfill backfill;

    public PopularityAdminController(LikeRateStore store, LikeRateBackfill backfill) {
        this.store = store;
        this.backfill = backfill;
    }

    /**
     * How much evidence the counters hold, and whether it is enough for the factorisation to
     * be worth building. That threshold is measured, not guessed, which is the point of
     * exposing this rather than deciding by feel.
     */
    @GetMapping("/density")
    public Mono<DensityView> density() {
        return store.density().map(density -> new DensityView(
                density.profiles(),
                density.impressions(),
                density.likes(),
                density.likesPerProfile(),
                density.impressionsPerProfile(),
                LikeRateStore.EMBEDDING_THRESHOLD,
                density.readyForEmbeddings()));
    }

    /** Rebuilds the counters from the consumer service's durable swipe history. */
    @PostMapping("/backfill")
    public Mono<LikeRateBackfill.Result> backfill() {
        return backfill.run();
    }

    public record DensityView(
            long profiles,
            long impressions,
            long likes,
            double likesPerProfile,
            double impressionsPerProfile,
            double embeddingThreshold,
            boolean readyForEmbeddings) {
    }
}
