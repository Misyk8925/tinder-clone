package com.tinder.deck.experiment;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One ranking experiment: which variants viewers are split across, and the salt that
 * makes the split reproducible.
 *
 * @param split variant name to relative traffic weight
 */
public record Experiment(
        UUID id,
        String name,
        String salt,
        String defaultVariant,
        ExperimentStatus status,
        Map<String, Integer> split,
        Instant createdAt,
        Instant startedAt,
        Instant stoppedAt) {
}
