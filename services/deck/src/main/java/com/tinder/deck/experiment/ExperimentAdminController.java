package com.tinder.deck.experiment;

import com.tinder.deck.adapters.ConsumerOutcomesHttp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Admin API for ranking experiments, consumed by the separate admin application.
 *
 * <p>The store is blocking JDBC while this service is WebFlux, so every call is offloaded
 * to {@link Schedulers#boundedElastic()} instead of running on an event-loop thread.
 *
 * <p>Like the existing {@code /api/v1/admin/deck} endpoints this carries no service-level
 * authentication: deck is an internal service and the {@code admin} role is enforced at
 * the gateway route.
 */
@RestController
@RequestMapping("/api/v1/admin/experiments")
@Slf4j
public class ExperimentAdminController {

    private final ExperimentStore store;
    private final ActiveExperimentProvider activeExperiments;
    private final ConsumerOutcomesHttp consumerOutcomes;
    private final OutcomeAggregator aggregator;

    public ExperimentAdminController(ExperimentStore store,
                                     ActiveExperimentProvider activeExperiments,
                                     ConsumerOutcomesHttp consumerOutcomes,
                                     OutcomeAggregator aggregator) {
        this.store = store;
        this.activeExperiments = activeExperiments;
        this.consumerOutcomes = consumerOutcomes;
        this.aggregator = aggregator;
    }

    @GetMapping
    public Mono<List<ExperimentView>> list() {
        return offload(() -> store.findAll().stream().map(ExperimentView::of).toList());
    }

    @GetMapping("/{id}")
    public Mono<ExperimentView> get(@PathVariable UUID id) {
        return offload(() -> ExperimentView.of(store.find(id).orElseThrow(() -> new ExperimentNotFoundException(id))));
    }

    @PostMapping
    public Mono<ResponseEntity<ExperimentView>> create(@Valid @RequestBody ExperimentRequest request) {
        return offload(() -> store.create(request.name(), request.salt(), request.defaultVariant(), request.split()))
                .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(ExperimentView.of(created)));
    }

    @PutMapping("/{id}")
    public Mono<ExperimentView> update(@PathVariable UUID id, @Valid @RequestBody ExperimentRequest request) {
        return offload(() -> ExperimentView.of(
                store.updateDraft(id, request.salt(), request.defaultVariant(), request.split())));
    }

    @PostMapping("/{id}/start")
    public Mono<ExperimentView> start(@PathVariable UUID id) {
        return offload(() -> {
            Experiment started = store.start(id);
            activeExperiments.refreshNow();
            return ExperimentView.of(started);
        });
    }

    @PostMapping("/{id}/stop")
    public Mono<ExperimentView> stop(@PathVariable UUID id) {
        return offload(() -> {
            Experiment stopped = store.stop(id);
            activeExperiments.refreshNow();
            return ExperimentView.of(stopped);
        });
    }

    /**
     * Outcomes of one experiment, per arm.
     *
     * <p>The window is the experiment's own lifetime: swipes before it started belong to
     * whatever ran before, and counting them would mix two populations.
     */
    @GetMapping("/{id}/results")
    public Mono<ResultsView> results(@PathVariable UUID id) {
        return offload(() -> store.find(id).orElseThrow(() -> new ExperimentNotFoundException(id)))
                .flatMap(experiment -> {
                    if (experiment.startedAt() == null) {
                        return Mono.error(new ExperimentConflictException(
                                "Experiment " + id + " has never run, so it has no outcomes"));
                    }
                    Instant from = experiment.startedAt();
                    Instant to = experiment.stoppedAt() != null ? experiment.stoppedAt() : Instant.now();
                    return consumerOutcomes.outcomesBetween(from, to)
                            .map(outcomes -> new ResultsView(experiment.name(), from, to,
                                    aggregator.aggregate(experiment, outcomes)));
                });
    }

    private static <T> Mono<T> offload(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic());
    }

    @ExceptionHandler(ExperimentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ExperimentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ExperimentConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ExperimentConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    public record ResultsView(String experiment, Instant from, Instant to, List<VariantOutcome> variants) {
    }

    public record ExperimentRequest(
            @NotBlank String name,
            @NotBlank String salt,
            @NotBlank String defaultVariant,
            @NotEmpty Map<String, Integer> split) {
    }

    public record ExperimentView(
            UUID id,
            String name,
            String salt,
            String defaultVariant,
            ExperimentStatus status,
            Map<String, Integer> split,
            Instant createdAt,
            Instant startedAt,
            Instant stoppedAt) {

        static ExperimentView of(Experiment experiment) {
            return new ExperimentView(experiment.id(), experiment.name(), experiment.salt(),
                    experiment.defaultVariant(), experiment.status(), experiment.split(),
                    experiment.createdAt(), experiment.startedAt(), experiment.stoppedAt());
        }
    }
}
