package com.tinder.deck.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ActiveExperimentProvider.
 *
 * <p>This component sits between a database and the deck build, so the cases that matter
 * are the unhappy ones: it must never turn a registry outage into a failed deck.
 */
@ExtendWith(MockitoExtension.class)
class ActiveExperimentProviderTest {

    @Mock
    private ExperimentStore store;

    @Test
    void hasNoActiveExperimentBeforeTheFirstRefresh() {
        // Given / When: a provider that has not refreshed yet
        ActiveExperimentProvider provider = new ActiveExperimentProvider(store);

        // Then: empty, which scores every viewer as the default variant
        assertThat(provider.current()).isEmpty();
    }

    @Test
    void publishesTheRunningExperiment() {
        // Given: one running experiment
        when(store.findRunning()).thenReturn(Optional.of(experiment("reciprocity", Map.of("baseline", 50, "reciprocal", 50))));
        ActiveExperimentProvider provider = new ActiveExperimentProvider(store);

        // When: refreshing
        provider.refresh();

        // Then: it becomes the active snapshot
        assertThat(provider.current()).hasValueSatisfying(active -> {
            assertThat(active.name()).isEqualTo("reciprocity");
            assertThat(active.split()).containsEntry("reciprocal", 50);
        });
    }

    @Test
    void clearsTheSnapshotWhenTheExperimentIsStopped() {
        // Given: a provider holding a running experiment
        when(store.findRunning())
                .thenReturn(Optional.of(experiment("reciprocity", Map.of("baseline", 50, "reciprocal", 50))))
                .thenReturn(Optional.empty());
        ActiveExperimentProvider provider = new ActiveExperimentProvider(store);
        provider.refresh();

        // When: the experiment is stopped and the next refresh runs
        provider.refresh();

        // Then: scoring goes back to the default variant
        assertThat(provider.current()).isEmpty();
    }

    @Test
    void keepsTheLastKnownExperimentWhenTheRegistryFails() {
        // Given: a provider that has seen a running experiment
        when(store.findRunning())
                .thenReturn(Optional.of(experiment("reciprocity", Map.of("baseline", 50, "reciprocal", 50))))
                .thenThrow(new IllegalStateException("connection refused"));
        ActiveExperimentProvider provider = new ActiveExperimentProvider(store);
        provider.refresh();

        // When: the database goes away
        provider.refresh();

        // Then: it neither throws nor forgets — NFR-6
        assertThat(provider.current()).hasValueSatisfying(active ->
                assertThat(active.name()).isEqualTo("reciprocity"));
    }

    @Test
    void staysEmptyWhenTheRegistryHasNeverAnswered() {
        // Given: a registry that is down from the start
        when(store.findRunning()).thenThrow(new IllegalStateException("connection refused"));
        ActiveExperimentProvider provider = new ActiveExperimentProvider(store);

        // When / Then: still no exception, and the deck scores as the default variant
        provider.refresh();
        assertThat(provider.current()).isEmpty();
    }

    private static Experiment experiment(String name, Map<String, Integer> split) {
        return new Experiment(UUID.randomUUID(), name, "salt-" + name, "baseline",
                ExperimentStatus.RUNNING, split, Instant.now(), Instant.now(), null);
    }
}
