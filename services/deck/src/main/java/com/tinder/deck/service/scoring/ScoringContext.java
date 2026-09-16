package com.tinder.deck.service.scoring;

import com.tinder.deck.service.popularity.LikeRates;

import java.util.UUID;

/**
 * Per-deck data a strategy needs but must not fetch itself.
 *
 * <p>Scoring runs once per candidate on a parallel scheduler. A strategy that reached for
 * Redis there would put one round trip per candidate on the deck build path, and deck scores
 * up to two thousand candidates at a time. The pipeline loads what is needed for the whole
 * pool in one batch and passes it down here instead.
 */
public record ScoringContext(LikeRates likeRates) {

    public static ScoringContext empty(double baseRate) {
        return new ScoringContext(LikeRates.empty(baseRate));
    }

    public double likeRateOf(UUID profileId) {
        return likeRates.rateOf(profileId);
    }

    public boolean isUnseen(UUID profileId) {
        return likeRates.isUnseen(profileId);
    }
}
