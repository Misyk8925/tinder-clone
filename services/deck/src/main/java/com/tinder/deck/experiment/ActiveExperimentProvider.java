package com.tinder.deck.experiment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the running experiment in memory so the deck build never reads the database.
 *
 * <p>A failed refresh keeps the previous snapshot and logs; a registry that has never
 * answered leaves the snapshot empty, which scores everyone as the default variant. The
 * deck build is never failed by this component.
 */
@Component
@Slf4j
public class ActiveExperimentProvider {

    private final ExperimentStore store;
    private final AtomicReference<Optional<ActiveExperiment>> snapshot = new AtomicReference<>(Optional.empty());

    public ActiveExperimentProvider(ExperimentStore store) {
        this.store = store;
    }

    public Optional<ActiveExperiment> current() {
        return snapshot.get();
    }

    @Scheduled(
            fixedDelayString = "${deck.scoring.experiment-refresh-ms:10000}",
            initialDelayString = "${deck.scoring.experiment-refresh-initial-delay-ms:0}")
    public void refresh() {
        try {
            Optional<ActiveExperiment> refreshed = store.findRunning().map(ActiveExperiment::of);
            Optional<ActiveExperiment> previous = snapshot.getAndSet(refreshed);
            if (!previous.map(ActiveExperiment::name).equals(refreshed.map(ActiveExperiment::name))) {
                log.info("Active ranking experiment changed: {} -> {}",
                        previous.map(ActiveExperiment::name).orElse("none"),
                        refreshed.map(ActiveExperiment::name).orElse("none"));
            }
        } catch (Exception e) {
            log.warn("Could not refresh the active experiment; keeping the last known one ({}): {}",
                    snapshot.get().map(ActiveExperiment::name).orElse("none"), e.toString());
        }
    }

    /** Lets an admin write take effect without waiting for the next scheduled refresh. */
    public void refreshNow() {
        refresh();
    }
}
