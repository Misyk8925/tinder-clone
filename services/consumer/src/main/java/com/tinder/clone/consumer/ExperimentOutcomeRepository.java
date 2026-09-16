package com.tinder.clone.consumer;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts swipes, likes and matches per viewer inside a time window.
 *
 * <p>The source is {@code swipe_event_outbox}, not {@code swipe_records} and not
 * {@code swipe_events}. {@code swipe_records} holds one row per pair with both decisions and
 * no timestamp, so it cannot answer "during this experiment". {@code swipe_events} has the
 * right shape but **nothing in the service ever writes to it** — a live run against the
 * running stack found it empty beside twelve thousand outbox rows. The outbox is the one
 * table that carries a swipe, its direction, its decision and when it happened.
 *
 * <p>A match belongs to both sides, so it is counted once for each of the two profiles.
 */
@Repository
public class ExperimentOutcomeRepository {

    private static final String SWIPES = """
            SELECT swiper_id,
                   COUNT(*) AS swipes,
                   COUNT(*) FILTER (WHERE (payload::jsonb ->> 'decision') = 'true') AS likes
            FROM swipe_event_outbox
            WHERE event_type = 'SWIPE_SAVED' AND created_at >= ?1 AND created_at < ?2
            GROUP BY swiper_id
            """;

    private static final String MATCHES = """
            SELECT profile_id, COUNT(*) AS matches FROM (
                SELECT profile1_id AS profile_id FROM match_event_outbox WHERE created_at >= ?1 AND created_at < ?2
                UNION ALL
                SELECT profile2_id AS profile_id FROM match_event_outbox WHERE created_at >= ?1 AND created_at < ?2
            ) AS sides
            GROUP BY profile_id
            """;

    private final EntityManager entityManager;

    public ExperimentOutcomeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ExperimentOutcomeController.ViewerOutcomeDto> outcomesBetween(Instant from, Instant to) {
        Map<String, long[]> byViewer = new LinkedHashMap<>();

        for (Object[] row : rows(SWIPES, from, to)) {
            long[] counts = byViewer.computeIfAbsent(String.valueOf(row[0]), ignored -> new long[3]);
            counts[0] = ((Number) row[1]).longValue();
            counts[1] = ((Number) row[2]).longValue();
        }
        for (Object[] row : rows(MATCHES, from, to)) {
            long[] counts = byViewer.computeIfAbsent(String.valueOf(row[0]), ignored -> new long[3]);
            counts[2] = ((Number) row[1]).longValue();
        }

        List<ExperimentOutcomeController.ViewerOutcomeDto> outcomes = new ArrayList<>(byViewer.size());
        byViewer.forEach((viewerId, counts) -> outcomes.add(
                new ExperimentOutcomeController.ViewerOutcomeDto(viewerId, counts[0], counts[1], counts[2])));
        return outcomes;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Instant from, Instant to) {
        return entityManager.createNativeQuery(sql)
                .setParameter(1, Timestamp.from(from))
                .setParameter(2, Timestamp.from(to))
                .getResultList();
    }
}
