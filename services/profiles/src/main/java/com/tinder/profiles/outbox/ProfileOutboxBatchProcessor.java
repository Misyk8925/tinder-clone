package com.tinder.profiles.outbox;

import com.tinder.profiles.config.OutboxPublisherProperties;
import com.tinder.profiles.outbox.model.OutboxPublishResult;
import com.tinder.profiles.outbox.model.OutboxRetryBackoffPolicy;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileOutboxBatchProcessor {

    private final ProfileEventOutboxRepository outboxRepository;
    private final ProfileOutboxEventDispatcher dispatcher;
    private final OutboxRetryBackoffPolicy retryBackoffPolicy;
    private final OutboxPublisherProperties properties;

    @Transactional
    public OutboxPublishResult publishNextBatch() {
        int batchSize = Math.max(1, properties.getBatchSize());
        int maxRetries = Math.max(1, properties.getMaxRetries());
        Instant now = Instant.now();
        List<ProfileEventOutbox> batch = outboxRepository.lockNextBatchForPublish(now, batchSize);

        if (batch.isEmpty()) {
            return OutboxPublishResult.EMPTY;
        }

        int published = 0;
        int failed = 0;
        int deadLettered = 0;

        for (ProfileEventOutbox outboxRow : batch) {
            try {
                dispatcher.publish(outboxRow);
                outboxRow.markPublished(Instant.now());
                published++;
            } catch (Exception ex) {
                String errorMessage = truncateError(ex.getMessage());
                int nextFailedAttemptCount = outboxRow.getRetryCount() + 1;
                failed++;

                if (nextFailedAttemptCount >= maxRetries) {
                    Instant deadLetteredAt = Instant.now();
                    outboxRow.markDeadLettered(deadLetteredAt, errorMessage);
                    deadLettered++;

                    log.error(
                            "Outbox event quarantined after max retries: rowId={}, eventId={}, profileId={}, retryCount={}, deadLetteredAt={}, cause={}",
                            outboxRow.getId(),
                            outboxRow.getEventId(),
                            outboxRow.getProfileId(),
                            outboxRow.getRetryCount(),
                            deadLetteredAt,
                            ex.getMessage()
                    );
                } else {
                    Instant nextAttemptAt = Instant.now().plus(retryBackoffPolicy.nextDelay(outboxRow.getRetryCount()));
                    outboxRow.scheduleRetry(nextAttemptAt, errorMessage);

                    log.warn(
                            "Outbox publish failed for rowId={}, eventId={}, profileId={}, retryCount={} nextAttemptAt={}. Cause: {}",
                            outboxRow.getId(),
                            outboxRow.getEventId(),
                            outboxRow.getProfileId(),
                            outboxRow.getRetryCount(),
                            nextAttemptAt,
                            ex.getMessage()
                    );
                }
            }
        }

        outboxRepository.saveAll(batch);
        return new OutboxPublishResult(batch.size(), published, failed, deadLettered);
    }

    private String truncateError(String message) {
        if (message == null) {
            return "Unknown error";
        }
        int maxLength = Math.max(64, properties.getMaxErrorLength());
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
