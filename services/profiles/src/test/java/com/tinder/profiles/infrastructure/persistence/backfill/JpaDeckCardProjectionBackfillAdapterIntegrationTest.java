package com.tinder.profiles.infrastructure.persistence.backfill;

import com.tinder.profiles.AbstractPostgresIntegrationTest;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillConflictException;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("acceptance")
@DisplayName("Feature: A failed Deck Card backfill resumes from its durable cursor")
class JpaDeckCardProjectionBackfillAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    DeckCardProjectionBackfillRunRepository runs;

    @Autowired
    JpaDeckCardProjectionBackfillAdapter adapter;

    @BeforeEach
    void clearRuns() {
        runs.deleteAll();
    }

    @Test
    @DisplayName("Scenario: Given a failed run with a committed page, when the same runId is retried, then RUNNING resumes without losing progress")
    void failedRunResumesWithItsCommittedProgress() {
        // Given
        UUID runId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-08-12T10:00:00Z");
        var failed = DeckCardProjectionBackfillRunJpaEntity.running(runId, 800, startedAt);
        failed.pageCommitted(cursor, 500, startedAt.plusSeconds(1));
        failed.markFailed("temporary database failure", startedAt.plusSeconds(2));
        runs.saveAndFlush(failed);

        // When
        var resumed = adapter.startOrResume(runId);

        // Then
        assertThat(resumed.status()).isEqualTo(DeckCardProjectionBackfillStatus.RUNNING);
        assertThat(resumed.lastProfileId()).isEqualTo(cursor);
        assertThat(resumed.processedCount()).isEqualTo(500);
        assertThat(resumed.startedAt()).isEqualTo(startedAt);
        assertThat(resumed.completedAt()).isNull();
        assertThat(resumed.lastError()).isNull();
    }

    @Test
    @DisplayName("Scenario: Given no active run, when two replicas start different runIds concurrently, then exactly one singleton run starts")
    void concurrentStartsAreSerializedByPostgres() throws Exception {
        // Given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> first = startAttempt(UUID.randomUUID(), ready, start);
        Callable<String> second = startAttempt(UUID.randomUUID(), ready, start);

        // When
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(first);
            var secondResult = executor.submit(second);
            ready.await();
            start.countDown();

            // Then
            assertThat(List.of(firstResult.get(), secondResult.get()))
                    .containsExactlyInAnyOrder("RUNNING", "CONFLICT");
        } finally {
            executor.shutdownNow();
        }
        assertThat(runs.count()).isEqualTo(1);
        assertThat(runs.existsByStatus(DeckCardProjectionBackfillStatus.RUNNING)).isTrue();
    }

    private Callable<String> startAttempt(UUID runId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            try {
                return adapter.startOrResume(runId).status().name();
            } catch (DeckCardProjectionBackfillConflictException expected) {
                return "CONFLICT";
            }
        };
    }
}
