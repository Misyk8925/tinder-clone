package com.tinder.clone.consumer;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Internal read model for ranking experiments.
 *
 * <p>Consumer owns swipes and matches, so it is the only service that can count them. It
 * deliberately knows nothing about experiments or variants: it returns per-viewer counts
 * for a time window, and the deck service — which owns the salt — buckets them into arms.
 * Teaching this service about experiments would put the same secret in two places.
 *
 * <p>Sits on the internal mTLS surface next to {@code /between/batch}, restricted to the
 * deck client by {@code ConsumerSecurityConfig}.
 */
@RestController
public class ExperimentOutcomeController {

    private final ExperimentOutcomeRepository repository;

    public ExperimentOutcomeController(ExperimentOutcomeRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/internal/experiment-outcomes")
    public List<ViewerOutcomeDto> outcomes(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return repository.outcomesBetween(from, to == null ? Instant.now() : to);
    }

    public record ViewerOutcomeDto(String viewerId, long swipes, long likes, long matches) {
    }
}
