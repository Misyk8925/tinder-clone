package com.tinder.clone.consumer;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts swipes received per profile, paged by profile id.
 *
 * <p>Paged rather than returned whole: a rebuild walks every profile that has ever been
 * swiped on, and a single response holding all of them would be a heap problem on both sides
 * of the call.
 *
 * <p>Reads {@code swipe_event_outbox}. The obvious-looking {@code swipe_events} table has the
 * right columns and no writer at all: a live run found it empty while the outbox held every
 * swipe the platform had ever seen.
 */
@Repository
public class ProfileSwipeCountsRepository {

    private static final String COUNTS = """
            SELECT swiped_id,
                   COUNT(*) AS impressions,
                   COUNT(*) FILTER (WHERE (payload::jsonb ->> 'decision') = 'true') AS likes
            FROM swipe_event_outbox
            WHERE event_type = 'SWIPE_SAVED'
              AND (CAST(?1 AS uuid) IS NULL OR swiped_id > CAST(?1 AS uuid))
            GROUP BY swiped_id
            ORDER BY swiped_id
            LIMIT ?2
            """;

    private final EntityManager entityManager;

    public ProfileSwipeCountsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<ProfileSwipeCountsController.ProfileSwipeCountDto> countsAfter(String after, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery(COUNTS)
                .setParameter(1, after)
                .setParameter(2, limit)
                .getResultList();

        List<ProfileSwipeCountsController.ProfileSwipeCountDto> counts = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            counts.add(new ProfileSwipeCountsController.ProfileSwipeCountDto(
                    String.valueOf(row[0]), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        return counts;
    }
}
