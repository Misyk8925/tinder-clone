package com.tinder.profiles.outbox;

import com.tinder.profiles.config.OutboxPublisherProperties;
import com.tinder.profiles.outbox.model.OutboxPublishResult;
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

    private OutboxPublisherProperties properties;
    private ProfileOutboxPublisherJob publisherJob;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        properties.setEnabled(true);
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(3);
        publisherJob = new ProfileOutboxPublisherJob(batchProcessor, properties);
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
        properties.setEnabled(false);

        publisherJob.publishPendingOutboxEvents();

        verify(batchProcessor, never()).publishNextBatch();
    }
}
