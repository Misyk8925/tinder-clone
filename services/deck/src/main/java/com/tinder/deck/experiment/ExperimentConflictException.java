package com.tinder.deck.experiment;

/** A transition the experiment lifecycle does not allow. */
public class ExperimentConflictException extends RuntimeException {

    public ExperimentConflictException(String message) {
        super(message);
    }
}
