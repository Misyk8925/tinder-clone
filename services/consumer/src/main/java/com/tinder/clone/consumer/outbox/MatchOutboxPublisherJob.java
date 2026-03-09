package com.tinder.clone.consumer.outbox;

import com.tinder.clone.consumer.outbox.config.OutboxPublisherProperties;
import com.tinder.clone.consumer.outbox.model.OutboxPublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchOutboxPublisherJob {

    private final MatchOutboxBatchProcessor batchProcessor;
    private final OutboxPublisherProperties properties;

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:1000}")
    public void publishPendingMatchOutboxEvents() {
        if (!properties.isEnabled()) {
            return;
        }

        int maxBatches = Math.max(1, properties.getMaxBatchesPerRun());
        int batchSize = Math.max(1, properties.getBatchSize());

        int totalClaimed = 0;
        int totalPublished = 0;
        int totalFailed = 0;
        int totalDeadLettered = 0;

        for (int i = 0; i < maxBatches; i++) {
            OutboxPublishResult result = batchProcessor.publishNextBatch();
            if (result.isEmpty()) {
                break;
            }

            totalClaimed += result.claimed();
            totalPublished += result.published();
            totalFailed += result.failed();
            totalDeadLettered += result.deadLettered();

            if (result.claimed() < batchSize) {
                break;
            }
        }

        if (totalClaimed > 0) {
            log.info(
                    "Match outbox publish cycle: claimed={}, published={}, failed={}, deadLettered={}",
                    totalClaimed, totalPublished, totalFailed, totalDeadLettered
            );
        }
    }
}
