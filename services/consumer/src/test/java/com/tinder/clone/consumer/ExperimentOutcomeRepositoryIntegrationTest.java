package com.tinder.clone.consumer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the experiment outcome queries.
 *
 * <p>These are native SQL against real tables, which is exactly the kind of code that
 * breaks silently when a column is renamed. They also pin the two decisions that are easy
 * to get wrong: the window comes from {@code swipe_event_outbox} — {@code swipe_records}
 * has no timestamp and {@code swipe_events} has no writer — and a match counts for both
 * profiles.
 */
@Transactional
class ExperimentOutcomeRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ExperimentOutcomeRepository repository;

    @Autowired
    private EntityManager entityManager;

    private final Instant windowStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.HOURS);
    private final Instant windowEnd = windowStart.plus(2, ChronoUnit.HOURS);

    @BeforeEach
    void clean() {
        entityManager.createNativeQuery("DELETE FROM swipe_event_outbox").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM match_event_outbox").executeUpdate();
    }

    @Test
    void countsSwipesAndLikesPerViewer() {
        UUID viewer = UUID.randomUUID();
        swipe(viewer, true, windowStart.plusSeconds(60));
        swipe(viewer, true, windowStart.plusSeconds(120));
        swipe(viewer, false, windowStart.plusSeconds(180));

        ExperimentOutcomeController.ViewerOutcomeDto outcome = only(viewer);

        assertThat(outcome.swipes()).isEqualTo(3);
        assertThat(outcome.likes()).isEqualTo(2);
    }

    @Test
    void ignoresActivityOutsideTheWindow() {
        UUID viewer = UUID.randomUUID();
        swipe(viewer, true, windowStart.minusSeconds(60));
        swipe(viewer, true, windowEnd.plusSeconds(60));
        swipe(viewer, true, windowStart.plusSeconds(60));

        assertThat(only(viewer).swipes())
                .as("swipes before the experiment started belong to whatever ran before it")
                .isEqualTo(1);
    }

    @Test
    void countsAMatchForBothProfiles() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        swipe(first, true, windowStart.plusSeconds(30));
        swipe(second, true, windowStart.plusSeconds(31));
        match(first, second, windowStart.plusSeconds(32));

        assertThat(only(first).matches()).isEqualTo(1);
        assertThat(only(second).matches()).isEqualTo(1);
    }

    @Test
    void reportsAViewerWithMatchesButNoSwipesInTheWindow() {
        UUID viewer = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        match(viewer, other, windowStart.plusSeconds(30));

        ExperimentOutcomeController.ViewerOutcomeDto outcome = only(viewer);

        assertThat(outcome.swipes()).isZero();
        assertThat(outcome.matches())
                .as("a match whose swipe fell outside the window must not vanish from the totals")
                .isEqualTo(1);
    }

    @Test
    void returnsNothingForAnEmptyWindow() {
        assertThat(repository.outcomesBetween(windowStart, windowEnd)).isEmpty();
    }

    private ExperimentOutcomeController.ViewerOutcomeDto only(UUID viewer) {
        List<ExperimentOutcomeController.ViewerOutcomeDto> outcomes =
                repository.outcomesBetween(windowStart, windowEnd);
        return outcomes.stream()
                .filter(outcome -> outcome.viewerId().equals(viewer.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no outcome for " + viewer + " in " + outcomes));
    }

    private void swipe(UUID swiper, boolean decision, Instant at) {
        swipe(swiper, UUID.randomUUID(), decision, at);
    }

    /**
     * Writes an outbox row the way the service does. Not {@code swipe_events}: that table has
     * the right columns and no writer, which a live run found the hard way.
     */
    private void swipe(UUID swiper, UUID swiped, boolean decision, Instant at) {
        entityManager.createNativeQuery("""
                        INSERT INTO swipe_event_outbox
                            (id, event_id, swiper_id, swiped_id, event_type, payload,
                             retry_count, created_at, next_attempt_at)
                        VALUES (?1, ?2, ?3, ?4, 'SWIPE_SAVED', ?5, 0, ?6, ?6)
                        """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, UUID.randomUUID())
                .setParameter(3, swiper)
                .setParameter(4, swiped)
                .setParameter(5, "{\"decision\":" + decision + "}")
                .setParameter(6, Timestamp.from(at))
                .executeUpdate();
    }

    private void match(UUID first, UUID second, Instant at) {
        entityManager.createNativeQuery("""
                        INSERT INTO match_event_outbox
                            (id, event_id, profile1_id, profile2_id, event_type, payload,
                             retry_count, created_at, next_attempt_at)
                        VALUES (?1, ?2, ?3, ?4, 'MATCH_CREATED', '{}', 0, ?5, ?5)
                        """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, UUID.randomUUID())
                .setParameter(3, first)
                .setParameter(4, second)
                .setParameter(5, Timestamp.from(at))
                .executeUpdate();
    }
}
