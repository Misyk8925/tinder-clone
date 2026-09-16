package com.tinder.deck.experiment;

/**
 * One arm of an experiment.
 *
 * <p>The headline number is matches per 100 swipes with a 95% Wilson interval, not a bare
 * mean: two arms whose intervals overlap have not been told apart, however different their
 * averages look.
 */
public record VariantOutcome(
        String variant,
        long viewers,
        long swipes,
        long likes,
        long matches,
        double likesPer100Swipes,
        double matchesPer100Swipes,
        double matchesPer100SwipesLower,
        double matchesPer100SwipesUpper) {
}
