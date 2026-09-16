package com.tinder.deck.experiment;

import java.util.UUID;

public class ExperimentNotFoundException extends RuntimeException {

    public ExperimentNotFoundException(UUID id) {
        super("Experiment " + id + " does not exist");
    }
}
