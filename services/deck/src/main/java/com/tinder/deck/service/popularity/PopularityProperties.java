package com.tinder.deck.service.popularity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Numbers behind the popularity ranking. Every one of them was measured offline in
 * {@code services/deck/load-tests/deck-sim} rather than chosen.
 */
@Data
@ConfigurationProperties(prefix = "deck.popularity")
public class PopularityProperties {

    /** Prior likes folded into every rate. */
    private double priorLikes = 1;

    /** Prior impressions folded into every rate. */
    private double priorShows = 8;

    /**
     * Share of each deck reserved for profiles with no recorded impressions.
     *
     * <p>Measured over six rounds of the ranker training on its own output: matches peak on a
     * flat 10-12% plateau and the counter's coverage keeps widening across it, so the top of
     * the plateau is taken. A quota is also invisible below roughly one over a viewer's
     * patience — reserved slots a viewer never reaches are not reserved slots.
     */
    private double newcomerQuota = 0.12;
}
