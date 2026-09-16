package com.tinder.deck.service.popularity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A candidate pool's like rates, plus who among them has never been shown at all.
 *
 * <p>The unseen set is not "whose rate equals the base rate": a profile can land on the base
 * rate by coincidence. Only zero impressions makes a profile a newcomer, and only newcomers
 * qualify for the reserved slots that keep them from being invisible forever.
 *
 * @param rates    candidate id to its smoothed like rate
 * @param unseen   candidates with no recorded impressions
 * @param baseRate the rate used for a candidate absent from {@code rates}
 */
public record LikeRates(Map<UUID, Double> rates, Set<UUID> unseen, double baseRate) {

    public static LikeRates empty(double baseRate) {
        return new LikeRates(Map.of(), Set.of(), baseRate);
    }

    public double rateOf(UUID profileId) {
        Double rate = rates.get(profileId);
        return rate != null ? rate : baseRate;
    }

    public boolean isUnseen(UUID profileId) {
        return unseen.contains(profileId);
    }
}
