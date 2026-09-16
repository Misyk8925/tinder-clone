package com.tinder.clone.consumer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the per-profile swipe counts that rebuild deck's like-rate counters.
 *
 * <p>The table these read is the one the service actually writes. A live run against the
 * running stack found `swipe_events` empty beside twelve thousand outbox rows, which would
 * have made every rebuild restore nothing at all while reporting success.
 */
@Transactional
class ProfileSwipeCountsRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProfileSwipeCountsRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clean() {
        entityManager.createNativeQuery("DELETE FROM swipe_event_outbox").executeUpdate();
    }

    @Test
    void countsImpressionsAndLikesPerSwipedProfile() {
        UUID popular = UUID.randomUUID();
        swipe(popular, true);
        swipe(popular, true);
        swipe(popular, false);

        ProfileSwipeCountsController.ProfileSwipeCountDto counts = only(popular);

        assertThat(counts.impressions()).isEqualTo(3);
        assertThat(counts.likes()).isEqualTo(2);
    }

    @Test
    void countsTheProfileThatWasSwipedOnNotTheOneSwiping() {
        UUID swiper = UUID.randomUUID();
        UUID swiped = UUID.randomUUID();
        swipeBetween(swiper, swiped, true);

        List<ProfileSwipeCountsController.ProfileSwipeCountDto> counts = repository.countsAfter(null, 100);

        // The whole point of this query is that it reads the other column from the one the
        // experiment outcomes use.
        assertThat(counts).extracting(ProfileSwipeCountsController.ProfileSwipeCountDto::profileId)
                .contains(swiped.toString())
                .doesNotContain(swiper.toString());
    }

    @Test
    void pagesByProfileIdSoARebuildCanWalkEveryone() {
        for (int i = 0; i < 5; i++) {
            swipe(UUID.randomUUID(), true);
        }

        List<ProfileSwipeCountsController.ProfileSwipeCountDto> first = repository.countsAfter(null, 2);
        assertThat(first).hasSize(2);

        List<ProfileSwipeCountsController.ProfileSwipeCountDto> second =
                repository.countsAfter(first.get(1).profileId(), 2);
        assertThat(second).hasSize(2);
        assertThat(second.get(0).profileId()).isGreaterThan(first.get(1).profileId());
    }

    @Test
    void returnsNothingWhenNoSwipeWasEverRecorded() {
        assertThat(repository.countsAfter(null, 100)).isEmpty();
    }

    private ProfileSwipeCountsController.ProfileSwipeCountDto only(UUID profile) {
        return repository.countsAfter(null, 500).stream()
                .filter(row -> row.profileId().equals(profile.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no counts for " + profile));
    }

    private void swipe(UUID swiped, boolean decision) {
        swipeBetween(UUID.randomUUID(), swiped, decision);
    }

    private void swipeBetween(UUID swiper, UUID swiped, boolean decision) {
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
                .setParameter(6, Timestamp.from(Instant.now()))
                .executeUpdate();
    }
}
