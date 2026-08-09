package com.tinder.profiles.infrastructure.messaging.outbox;

import com.tinder.profiles.config.props.OutboxPublisherProperties;
import com.tinder.profiles.infrastructure.messaging.outbox.model.OutboxPublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileOutboxPublisherJobComponentTest {

    @Mock
    private ProfileOutboxBatchProcessor batchProcessor;

    private ProfileOutboxPublisherJob publisherJob;

    @BeforeEach
    void setUp() {
        publisherJob = new ProfileOutboxPublisherJob(batchProcessor, properties(true));
    }

    private static OutboxPublisherProperties properties(boolean enabled) {
        return new OutboxPublisherProperties(
                enabled, 2, 3, 1000L, 1000L, 60000L, 2.0, 5000L, 1000, 10);
    }

    @Test
    void publishPendingOutboxEvents_ShouldStopWhenBatchNotFull() {
        when(batchProcessor.publishNextBatch())
                .thenReturn(new OutboxPublishResult(2, 2, 0, 0))
                .thenReturn(new OutboxPublishResult(1, 1, 0, 0));

        publisherJob.publishPendingOutboxEvents();

        verify(batchProcessor, times(2)).publishNextBatch();
    }

    @Test
    void publishPendingOutboxEvents_ShouldStopAtConfiguredMaxBatches() {
        when(batchProcessor.publishNextBatch())
                .thenReturn(new OutboxPublishResult(2, 2, 0, 0))
                .thenReturn(new OutboxPublishResult(2, 2, 0, 0))
                .thenReturn(new OutboxPublishResult(2, 2, 0, 0))
                .thenReturn(new OutboxPublishResult(2, 2, 0, 0));

        publisherJob.publishPendingOutboxEvents();

        verify(batchProcessor, times(3)).publishNextBatch();
    }

    @Test
    void publishPendingOutboxEvents_ShouldSkipWhenDisabled() {
        publisherJob = new ProfileOutboxPublisherJob(batchProcessor, properties(false));

        publisherJob.publishPendingOutboxEvents();

        verify(batchProcessor, never()).publishNextBatch();
    }
}
