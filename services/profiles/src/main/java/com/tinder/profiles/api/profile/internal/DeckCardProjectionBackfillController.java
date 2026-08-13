package com.tinder.profiles.api.profile.internal;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillRun;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillStatus;
import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillConflictException;
import com.tinder.profiles.application.profile.usecase.DeckCardProjectionBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** mTLS-only maintenance entry point; it is never invoked on application startup. */
@RestController
@RequestMapping("/api/v1/profiles/internal/deck-card-projection/backfills")
@RequiredArgsConstructor
public class DeckCardProjectionBackfillController {

    private final DeckCardProjectionBackfillService backfill;

    @PostMapping("/{runId}")
    public ResponseEntity<DeckCardProjectionBackfillRun> startOrResume(@PathVariable UUID runId) {
        DeckCardProjectionBackfillRun run = backfill.startOrResume(runId);
        return run.status() == DeckCardProjectionBackfillStatus.ENQUEUED
                ? ResponseEntity.accepted().body(run)
                : ResponseEntity.ok(run);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<DeckCardProjectionBackfillRun> status(@PathVariable UUID runId) {
        return backfill.status(runId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(DeckCardProjectionBackfillConflictException.class)
    public ResponseEntity<MaintenanceProblem> conflict(DeckCardProjectionBackfillConflictException failure) {
        return ResponseEntity.status(409)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new MaintenanceProblem(
                        "about:blank",
                        "Deck Card projection backfill conflict",
                        409,
                        failure.getMessage()));
    }

    public record MaintenanceProblem(String type, String title, int status, String detail) {
    }
}
