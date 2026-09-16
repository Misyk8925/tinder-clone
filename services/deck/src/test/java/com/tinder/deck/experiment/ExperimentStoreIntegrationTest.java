package com.tinder.deck.experiment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ExperimentStore against a real PostgreSQL.
 *
 * <p>Covers the lifecycle rules the registry has to enforce itself, because they protect
 * the reproducibility of past variant assignments: a running experiment cannot be edited,
 * and two experiments cannot run at once.
 */
@Testcontainers
class ExperimentStoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static DataSource dataSource;
    private ExperimentStore store;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName("org.postgresql.Driver");
        dataSource = source;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void reset() {
        new JdbcTemplate(dataSource).execute("TRUNCATE deck_experiment CASCADE");
        // Driven directly rather than through the Spring proxy, so @Transactional does not
        // apply here; every assertion below holds statement by statement.
        this.store = new ExperimentStore(JdbcClient.create(dataSource));
    }

    @Test
    void createsADraftWithItsSplit() {
        Experiment created = store.create("reciprocity", "salt-1", "baseline",
                Map.of("baseline", 50, "reciprocal", 50));

        assertThat(created.status()).isEqualTo(ExperimentStatus.DRAFT);
        assertThat(created.split()).containsEntry("baseline", 50).containsEntry("reciprocal", 50);
        assertThat(store.findRunning()).isEmpty();
    }

    @Test
    void refusesASecondExperimentWithTheSameName() {
        store.create("reciprocity", "salt-1", "baseline", Map.of("baseline", 100));

        assertThatThrownBy(() -> store.create("reciprocity", "salt-2", "baseline", Map.of("baseline", 100)))
                .isInstanceOf(ExperimentConflictException.class);
    }

    @Test
    void startsADraftAndPublishesItAsRunning() {
        Experiment draft = store.create("reciprocity", "salt-1", "baseline",
                Map.of("baseline", 50, "reciprocal", 50));

        Experiment running = store.start(draft.id());

        assertThat(running.status()).isEqualTo(ExperimentStatus.RUNNING);
        assertThat(running.startedAt()).isNotNull();
        assertThat(store.findRunning()).hasValueSatisfying(found ->
                assertThat(found.name()).isEqualTo("reciprocity"));
    }

    @Test
    void refusesToRunTwoExperimentsAtOnce() {
        Experiment first = store.create("first", "salt-1", "baseline", Map.of("baseline", 100));
        Experiment second = store.create("second", "salt-2", "baseline", Map.of("baseline", 100));
        store.start(first.id());

        assertThatThrownBy(() -> store.start(second.id()))
                .isInstanceOf(ExperimentConflictException.class);
    }

    @Test
    void refusesToEditARunningExperiment() {
        // Given: a running experiment — FR-9
        Experiment draft = store.create("reciprocity", "salt-1", "baseline", Map.of("baseline", 100));
        store.start(draft.id());

        // When / Then: changing the salt would rewrite every past assignment
        assertThatThrownBy(() -> store.updateDraft(draft.id(), "salt-2", "baseline", Map.of("baseline", 100)))
                .isInstanceOf(ExperimentConflictException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    void editsADraftInPlace() {
        Experiment draft = store.create("reciprocity", "salt-1", "baseline", Map.of("baseline", 100));

        Experiment edited = store.updateDraft(draft.id(), "salt-2", "baseline",
                Map.of("baseline", 30, "reciprocal", 70));

        assertThat(edited.salt()).isEqualTo("salt-2");
        assertThat(edited.split()).containsEntry("reciprocal", 70).hasSize(2);
    }

    @Test
    void stopsARunningExperimentAndFreesTheSlot() {
        Experiment first = store.create("first", "salt-1", "baseline", Map.of("baseline", 100));
        Experiment second = store.create("second", "salt-2", "baseline", Map.of("baseline", 100));
        store.start(first.id());

        store.stop(first.id());

        assertThat(store.findRunning()).isEmpty();
        assertThat(store.start(second.id()).status()).isEqualTo(ExperimentStatus.RUNNING);
    }

    @Test
    void refusesAnExperimentWithoutAPositiveWeight() {
        assertThatThrownBy(() -> store.create("empty", "salt-1", "baseline", Map.of("baseline", 0)))
                .isInstanceOf(ExperimentConflictException.class);
    }

    @Test
    void reportsAMissingExperiment() {
        UUID missing = UUID.randomUUID();
        assertThat(store.find(missing)).isEmpty();
        assertThatThrownBy(() -> store.start(missing)).isInstanceOf(ExperimentNotFoundException.class);
    }
}
