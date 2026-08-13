package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillRun;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import com.tinder.profiles.application.profile.port.out.DeckCardProjectionBackfillPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("acceptance")
@DisplayName("Feature: An operator resumes Deck Card projection backfill")
class DeckCardProjectionBackfillServiceTest {

    @Mock
    DeckCardProjectionBackfillPort port;

    DeckCardProjectionBackfillService service;

    @BeforeEach
    void setUp() {
        service = new DeckCardProjectionBackfillService(port);
    }

    @Test
    @DisplayName("Scenario: Given a durable run cursor, when the same run is resumed, then bounded pages are enqueued until completion")
    void explicitRunResumesInBoundedPagesUntilAllRowsAreEnqueued() {
        // Given
        UUID runId = UUID.randomUUID();
        UUID cursor = UUID.randomUUID();
        DeckCardProjectionBackfillRun running = run(runId, DeckCardProjectionBackfillStatus.RUNNING, null, 0);
        DeckCardProjectionBackfillRun pageCommitted = run(
                runId, DeckCardProjectionBackfillStatus.RUNNING, cursor, 500);
        DeckCardProjectionBackfillRun enqueued = run(
                runId, DeckCardProjectionBackfillStatus.ENQUEUED, cursor, 700);
        DeckCardProjectionBackfillRun completed = run(
                runId, DeckCardProjectionBackfillStatus.COMPLETED, cursor, 700);

        when(port.startOrResume(runId)).thenReturn(running);
        when(port.enqueueNextPage(runId, 500)).thenReturn(pageCommitted, enqueued);
        when(port.refreshStatus(runId)).thenReturn(Optional.of(completed));

        // When
        assertThat(service.startOrResume(runId)).isEqualTo(completed);

        // Then
        verify(port).startOrResume(runId);
        verify(port, org.mockito.Mockito.times(2)).enqueueNextPage(runId, 500);
        verify(port).refreshStatus(runId);
    }

    @Test
    @DisplayName("Scenario: Given a failing backfill page, when the run stops, then a sanitized single-line failure is persisted")
    void failureIsPersistedWithSanitizedSingleLineMessage() {
        // Given
        UUID runId = UUID.randomUUID();
        when(port.startOrResume(runId))
                .thenReturn(run(runId, DeckCardProjectionBackfillStatus.RUNNING, null, 0));
        when(port.enqueueNextPage(runId, 500))
                .thenThrow(new IllegalStateException("database\nconnection\tfailed"));

        // When / Then
        assertThatThrownBy(() -> service.startOrResume(runId))
                .isInstanceOf(IllegalStateException.class);
        verify(port).markFailed(runId, "IllegalStateException: database connection failed");
    }

    private DeckCardProjectionBackfillRun run(
            UUID runId,
            DeckCardProjectionBackfillStatus status,
            UUID cursor,
            long processed
    ) {
        Instant now = Instant.parse("2026-08-11T12:00:00Z");
        return new DeckCardProjectionBackfillRun(
                runId, status, cursor, processed, 700, now, now,
                status == DeckCardProjectionBackfillStatus.COMPLETED ? now : null, null);
    }
}
