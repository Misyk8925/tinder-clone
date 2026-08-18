package com.tinder.profiles.infrastructure.messaging.outbox;

import com.tinder.profiles.config.props.OutboxPublisherProperties;
import com.tinder.profiles.infrastructure.messaging.outbox.model.OutboxPublishResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileOutboxPublisherJob {

    private final ProfileOutboxBatchProcessor batchProcessor;
    private final OutboxPublisherProperties properties;

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:1000}")
    public void publishPendingOutboxEvents() {
        if (!properties.enabled()) {
            return;
        }

        int maxBatches = Math.max(1, properties.maxBatchesPerRun());
        int batchSize = Math.max(1, properties.batchSize());

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
                    "Outbox publish cycle finished: claimed={}, published={}, failed={}, deadLettered={}",
                    totalClaimed,
                    totalPublished,
                    totalFailed,
                    totalDeadLettered
            );
        }
    }
}
