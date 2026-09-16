package com.tinder.deck.experiment;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable home of the experiment registry.
 *
 * <p>It is a database rather than the deck's Redis on purpose: the deck cache is
 * deliberately ephemeral and rebuildable, while losing an experiment's salt would make
 * every past assignment unrecoverable.
 */
@Repository
public class ExperimentStore {

    private final JdbcClient jdbc;

    public ExperimentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Experiment create(String name, String salt, String defaultVariant, Map<String, Integer> split) {
        requireSplit(split);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.sql("""
                            INSERT INTO deck_experiment
                                (experiment_id, name, salt, default_variant, status, created_at)
                            VALUES (?, ?, ?, ?, 'DRAFT', ?)
                            """)
                    .params(id, name, salt, defaultVariant, java.sql.Timestamp.from(now))
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ExperimentConflictException("Experiment '" + name + "' already exists");
        }
        writeSplit(id, split);
        return find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
    }

    /**
     * Edits a draft. A running or stopped experiment is refused: changing its salt would
     * silently rewrite which variant every past viewer was in, and the assignment is
     * recomputed from that salt rather than stored.
     */
    @Transactional
    public Experiment updateDraft(UUID id, String salt, String defaultVariant, Map<String, Integer> split) {
        requireSplit(split);
        Experiment existing = find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
        if (existing.status() != ExperimentStatus.DRAFT) {
            throw new ExperimentConflictException(
                    "Experiment " + id + " is " + existing.status() + "; only a DRAFT can be edited");
        }
        jdbc.sql("UPDATE deck_experiment SET salt = ?, default_variant = ? WHERE experiment_id = ?")
                .params(salt, defaultVariant, id)
                .update();
        jdbc.sql("DELETE FROM deck_experiment_variant WHERE experiment_id = ?").param(id).update();
        writeSplit(id, split);
        return find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
    }

    @Transactional
    public Experiment start(UUID id) {
        Experiment existing = find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
        if (existing.status() != ExperimentStatus.DRAFT) {
            throw new ExperimentConflictException(
                    "Experiment " + id + " is " + existing.status() + "; only a DRAFT can be started");
        }
        try {
            jdbc.sql("UPDATE deck_experiment SET status = 'RUNNING', started_at = ? WHERE experiment_id = ?")
                    .params(java.sql.Timestamp.from(Instant.now()), id)
                    .update();
        } catch (DuplicateKeyException e) {
            // The partial unique index refuses a second running experiment.
            throw new ExperimentConflictException("Another experiment is already running");
        }
        return find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
    }

    @Transactional
    public Experiment stop(UUID id) {
        Experiment existing = find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
        if (existing.status() != ExperimentStatus.RUNNING) {
            throw new ExperimentConflictException(
                    "Experiment " + id + " is " + existing.status() + "; only a RUNNING experiment can be stopped");
        }
        jdbc.sql("UPDATE deck_experiment SET status = 'STOPPED', stopped_at = ? WHERE experiment_id = ?")
                .params(java.sql.Timestamp.from(Instant.now()), id)
                .update();
        return find(id).orElseThrow(() -> new ExperimentNotFoundException(id));
    }

    public Optional<Experiment> find(UUID id) {
        return jdbc.sql(SELECT_ONE).param(id).query(ExperimentStore::mapRow).optional()
                .map(row -> withSplit(row, readSplit(id)));
    }

    public List<Experiment> findAll() {
        List<Experiment> rows = jdbc.sql(SELECT_ALL).query(ExperimentStore::mapRow).list();
        List<Experiment> withSplits = new ArrayList<>(rows.size());
        for (Experiment row : rows) {
            withSplits.add(withSplit(row, readSplit(row.id())));
        }
        return withSplits;
    }

    public Optional<Experiment> findRunning() {
        return jdbc.sql(SELECT_RUNNING).query(ExperimentStore::mapRow).optional()
                .map(row -> withSplit(row, readSplit(row.id())));
    }

    private void writeSplit(UUID id, Map<String, Integer> split) {
        split.forEach((variant, weight) ->
                jdbc.sql("INSERT INTO deck_experiment_variant (experiment_id, variant, weight) VALUES (?, ?, ?)")
                        .params(id, variant, weight)
                        .update());
    }

    private Map<String, Integer> readSplit(UUID id) {
        Map<String, Integer> split = new LinkedHashMap<>();
        jdbc.sql("SELECT variant, weight FROM deck_experiment_variant WHERE experiment_id = ? ORDER BY variant")
                .param(id)
                .query((rs, rowNum) -> Map.entry(rs.getString("variant"), rs.getInt("weight")))
                .list()
                .forEach(entry -> split.put(entry.getKey(), entry.getValue()));
        return split;
    }

    private static void requireSplit(Map<String, Integer> split) {
        if (split == null || split.isEmpty()) {
            throw new ExperimentConflictException("An experiment needs at least one variant");
        }
        boolean anyPositive = split.values().stream().anyMatch(weight -> weight != null && weight > 0);
        if (!anyPositive) {
            throw new ExperimentConflictException("At least one variant needs a positive weight");
        }
    }

    private static Experiment withSplit(Experiment experiment, Map<String, Integer> split) {
        return new Experiment(experiment.id(), experiment.name(), experiment.salt(), experiment.defaultVariant(),
                experiment.status(), split, experiment.createdAt(), experiment.startedAt(), experiment.stoppedAt());
    }

    private static Experiment mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Experiment(
                rs.getObject("experiment_id", UUID.class),
                rs.getString("name"),
                rs.getString("salt"),
                rs.getString("default_variant"),
                ExperimentStatus.valueOf(rs.getString("status")),
                Map.of(),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "stopped_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static final String COLUMNS =
            "experiment_id, name, salt, default_variant, status, created_at, started_at, stopped_at";
    private static final String SELECT_ONE =
            "SELECT " + COLUMNS + " FROM deck_experiment WHERE experiment_id = ?";
    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM deck_experiment ORDER BY created_at DESC";
    private static final String SELECT_RUNNING =
            "SELECT " + COLUMNS + " FROM deck_experiment WHERE status = 'RUNNING'";
}
