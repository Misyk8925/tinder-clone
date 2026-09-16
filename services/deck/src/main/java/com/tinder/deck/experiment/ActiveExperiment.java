package com.tinder.deck.experiment;

import java.util.Map;

/**
 * The running experiment reduced to what scoring needs. Held in memory so the deck build
 * never reads the database.
 */
public record ActiveExperiment(String name, String salt, String defaultVariant, Map<String, Integer> split) {

    static ActiveExperiment of(Experiment experiment) {
        return new ActiveExperiment(
                experiment.name(), experiment.salt(), experiment.defaultVariant(), Map.copyOf(experiment.split()));
    }
}
