package com.tinder.profiles.application.profile.port.out;

import com.tinder.profiles.application.profile.model.DeckCardProjectionBackfillRun;

import java.util.Optional;
import java.util.UUID;

public interface DeckCardProjectionBackfillPort {

    DeckCardProjectionBackfillRun startOrResume(UUID runId);

    DeckCardProjectionBackfillRun enqueueNextPage(UUID runId, int pageSize);

    Optional<DeckCardProjectionBackfillRun> refreshStatus(UUID runId);

    DeckCardProjectionBackfillRun markFailed(UUID runId, String sanitizedError);
}
