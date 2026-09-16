package com.tinder.deck.experiment;

import com.tinder.deck.adapters.ConsumerOutcomesHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP contract of the admin API the separate admin application talks to.
 *
 * <p>The status codes are the point: a refused salt change has to arrive as 409 rather
 * than a 500, because that refusal is what protects the reproducibility of past variant
 * assignments.
 */
@WebFluxTest(ExperimentAdminController.class)
class ExperimentAdminControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ExperimentStore store;

    @MockitoBean
    private ActiveExperimentProvider activeExperiments;

    @MockitoBean
    private ConsumerOutcomesHttp consumerOutcomes;

    @MockitoBean
    private OutcomeAggregator aggregator;

    @Test
    void createsADraftExperiment() {
        when(store.create(eq("reciprocity"), eq("salt-1"), eq("baseline"), any()))
                .thenReturn(experiment("reciprocity", ExperimentStatus.DRAFT));

        webTestClient.post().uri("/api/v1/admin/experiments")
                .bodyValue(Map.of("name", "reciprocity", "salt", "salt-1", "defaultVariant", "baseline",
                        "split", Map.of("baseline", 50, "reciprocal", 50)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("reciprocity")
                .jsonPath("$.status").isEqualTo("DRAFT");
    }

    @Test
    void listsExperiments() {
        when(store.findAll()).thenReturn(List.of(experiment("reciprocity", ExperimentStatus.RUNNING)));

        webTestClient.get().uri("/api/v1/admin/experiments")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("RUNNING");
    }

    @Test
    void startingAnExperimentRefreshesTheActiveSnapshot() {
        UUID id = UUID.randomUUID();
        when(store.start(id)).thenReturn(experiment("reciprocity", ExperimentStatus.RUNNING));

        webTestClient.post().uri("/api/v1/admin/experiments/{id}/start", id)
                .exchange()
                .expectStatus().isOk();

        // The deck must not keep scoring on the old snapshot until the next scheduled poll
        verify(activeExperiments).refreshNow();
    }

    @Test
    void reportsAConflictWhenARunningExperimentIsEdited() {
        UUID id = UUID.randomUUID();
        when(store.updateDraft(eq(id), any(), any(), any()))
                .thenThrow(new ExperimentConflictException("Experiment " + id + " is RUNNING"));

        webTestClient.put().uri("/api/v1/admin/experiments/{id}", id)
                .bodyValue(Map.of("name", "reciprocity", "salt", "salt-2", "defaultVariant", "baseline",
                        "split", Map.of("baseline", 100)))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void reportsNotFoundForAMissingExperiment() {
        UUID id = UUID.randomUUID();
        when(store.find(id)).thenReturn(Optional.empty());

        webTestClient.get().uri("/api/v1/admin/experiments/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsAnExperimentWithoutVariants() {
        webTestClient.post().uri("/api/v1/admin/experiments")
                .bodyValue(Map.of("name", "reciprocity", "salt", "salt-1", "defaultVariant", "baseline",
                        "split", Map.of()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reportsOutcomesForAnExperimentThatHasRun() {
        UUID id = UUID.randomUUID();
        Experiment running = experiment("reciprocity", ExperimentStatus.RUNNING);
        when(store.find(id)).thenReturn(Optional.of(running));
        when(consumerOutcomes.outcomesBetween(any(), any())).thenReturn(Mono.just(List.of()));
        when(aggregator.aggregate(any(), any())).thenReturn(List.of(
                new VariantOutcome("baseline", 300, 6000, 1200, 240, 20.0, 4.0, 3.5, 4.5),
                new VariantOutcome("reciprocal", 300, 6000, 1300, 320, 21.7, 5.3, 4.8, 5.9)));

        webTestClient.get().uri("/api/v1/admin/experiments/{id}/results", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.experiment").isEqualTo("reciprocity")
                .jsonPath("$.variants[1].variant").isEqualTo("reciprocal")
                .jsonPath("$.variants[1].matchesPer100Swipes").isEqualTo(5.3)
                .jsonPath("$.variants[1].matchesPer100SwipesLower").isEqualTo(4.8);
    }

    @Test
    void refusesResultsForAnExperimentThatNeverRan() {
        // Given: a draft, which has no window to count over
        UUID id = UUID.randomUUID();
        when(store.find(id)).thenReturn(Optional.of(experiment("reciprocity", ExperimentStatus.DRAFT)));

        // When / Then: a conflict, not an empty result that would read as "no effect"
        webTestClient.get().uri("/api/v1/admin/experiments/{id}/results", id)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private static Experiment experiment(String name, ExperimentStatus status) {
        return new Experiment(UUID.randomUUID(), name, "salt-1", "baseline", status,
                Map.of("baseline", 50, "reciprocal", 50), Instant.now(),
                status == ExperimentStatus.DRAFT ? null : Instant.now(), null);
    }
}
