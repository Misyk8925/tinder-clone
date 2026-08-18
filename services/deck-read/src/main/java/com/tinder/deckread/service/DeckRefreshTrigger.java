package com.tinder.deckread.service;

import com.tinder.deckread.messaging.DeckMaterializationRequester;
import com.tinder.deckread.messaging.MaterializationReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/** Starts best-effort refresh work without coupling a usable HTTP response to Kafka availability. */
@ApplicationScoped
public class DeckRefreshTrigger {

    private static final Logger LOG = Logger.getLogger(DeckRefreshTrigger.class);

    private final DeckMaterializationRequester requester;
    private final Counter failures;

    @Inject
    public DeckRefreshTrigger(DeckMaterializationRequester requester, MeterRegistry meters) {
        this.requester = requester;
        this.failures = meters.counter("deck_read_materialization_requests", "outcome", "failed");
    }

    public void request(UUID viewerProfileId, MaterializationReason reason) {
        try {
            requester.request(viewerProfileId, reason).subscribe().with(
                    ignored -> { },
                    failure -> recordFailure(viewerProfileId, reason, failure));
        } catch (RuntimeException failure) {
            recordFailure(viewerProfileId, reason, failure);
        }
    }

    private void recordFailure(UUID viewerProfileId, MaterializationReason reason, Throwable failure) {
        failures.increment();
        LOG.warnf(failure, "Unable to request Deck Read materialization for viewer %s (%s)",
                viewerProfileId, reason);
    }
}
