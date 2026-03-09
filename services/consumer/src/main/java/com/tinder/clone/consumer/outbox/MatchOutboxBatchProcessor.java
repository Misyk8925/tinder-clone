package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.outbox.config.OutboxPublisherProperties;
import com.tinder.clone.consumer.outbox.model.MatchEventOutbox;
import com.tinder.clone.consumer.outbox.model.OutboxPublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchOutboxBatchProcessor {

    private final MatchEventOutboxRepository outboxRepository;
    private final MatchOutboxEventDispatcher dispatcher;
    private final OutboxRetryBackoffPolicy retryBackoffPolicy;
    private final OutboxPublisherProperties properties;

    @Transactional
    public OutboxPublishResult publishNextBatch() {
        int batchSize = Math.max(1, properties.getBatchSize());
        int maxRetries = Math.max(1, properties.getMaxRetries());
        Instant now = Instant.now();

        List<MatchEventOutbox> batch = outboxRepository.lockNextBatchForPublish(now, batchSize);
        if (batch.isEmpty()) {
            return OutboxPublishResult.EMPTY;
        }

        int published = 0;
        int failed = 0;
        int deadLettered = 0;

        for (MatchEventOutbox outboxRow : batch) {
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
                            "Match outbox event quarantined after max retries: rowId={}, eventId={}, profile1Id={}, profile2Id={}, retryCount={}, cause={}",
                            outboxRow.getId(), outboxRow.getEventId(),
                            outboxRow.getProfile1Id(), outboxRow.getProfile2Id(),
                            outboxRow.getRetryCount(), ex.getMessage()
                    );
                } else {
                    Instant nextAttemptAt = Instant.now().plus(retryBackoffPolicy.nextDelay(outboxRow.getRetryCount()));
                    outboxRow.scheduleRetry(nextAttemptAt, errorMessage);

                    log.warn(
                            "Match outbox publish failed: rowId={}, eventId={}, retryCount={}, nextAttemptAt={}, cause={}",
                            outboxRow.getId(), outboxRow.getEventId(),
                            outboxRow.getRetryCount(), nextAttemptAt, ex.getMessage()
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
