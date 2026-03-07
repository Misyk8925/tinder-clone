package com.tinder.profiles.outbox;

import com.tinder.profiles.config.OutboxPublisherProperties;
import com.tinder.profiles.outbox.model.OutboxPublishResult;
import com.tinder.profiles.outbox.model.OutboxRetryBackoffPolicy;
import com.tinder.profiles.outbox.model.ProfileEventOutbox;
import com.tinder.profiles.outbox.model.ProfileOutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileOutboxBatchProcessorTest {

    @Mock
    private ProfileEventOutboxRepository outboxRepository;

    @Mock
    private ProfileOutboxEventDispatcher dispatcher;

    private ProfileOutboxBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setBatchSize(10);
        properties.setInitialBackoffMs(100);
        properties.setMaxBackoffMs(2000);
        properties.setBackoffMultiplier(2.0);
        properties.setMaxErrorLength(80);
        properties.setMaxRetries(3);

        OutboxRetryBackoffPolicy backoffPolicy = new OutboxRetryBackoffPolicy(properties);
        batchProcessor = new ProfileOutboxBatchProcessor(outboxRepository, dispatcher, backoffPolicy, properties);
    }

    @Test
    void publishNextBatch_ShouldMarkRowsAsPublishedOnSuccess() {
        ProfileEventOutbox row1 = pendingRow(ProfileOutboxEventType.PROFILE_CREATED);
        ProfileEventOutbox row2 = pendingRow(ProfileOutboxEventType.PROFILE_UPDATED);
        List<ProfileEventOutbox> batch = List.of(row1, row2);

        when(outboxRepository.lockNextBatchForPublish(any(), anyInt())).thenReturn(batch);
        when(outboxRepository.saveAll(batch)).thenReturn(batch);

        OutboxPublishResult result = batchProcessor.publishNextBatch();

        assertThat(result).isEqualTo(new OutboxPublishResult(2, 2, 0, 0));
        assertThat(row1.getPublishedAt()).isNotNull();
        assertThat(row2.getPublishedAt()).isNotNull();
        assertThat(row1.getLastError()).isNull();
        assertThat(row2.getLastError()).isNull();
        verify(dispatcher).publish(row1);
        verify(dispatcher).publish(row2);
        verify(outboxRepository).saveAll(batch);
    }

    @Test
    void publishNextBatch_ShouldScheduleRetryOnFailure() {
        ProfileEventOutbox row = pendingRow(ProfileOutboxEventType.PROFILE_DELETED);
        when(outboxRepository.lockNextBatchForPublish(any(), anyInt())).thenReturn(List.of(row));
        when(outboxRepository.saveAll(anyList())).thenReturn(List.of(row));

        doThrow(new IllegalStateException("Kafka send failure ".repeat(20)))
                .when(dispatcher)
                .publish(row);

        Instant before = Instant.now();
        OutboxPublishResult result = batchProcessor.publishNextBatch();

        assertThat(result).isEqualTo(new OutboxPublishResult(1, 0, 1, 0));
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getDeadLetteredAt()).isNull();
        assertThat(row.getLastError()).isNotBlank();
        assertThat(row.getLastError().length()).isLessThanOrEqualTo(80);
        assertThat(row.getNextAttemptAt()).isAfter(before);
        verify(outboxRepository).saveAll(List.of(row));
    }

    @Test
    void publishNextBatch_ShouldQuarantineAfterMaxRetries() {
        ProfileEventOutbox row = pendingRow(ProfileOutboxEventType.PROFILE_UPDATED);
        row.setRetryCount(2); // maxRetries=3 => this failure should dead-letter

        when(outboxRepository.lockNextBatchForPublish(any(), anyInt())).thenReturn(List.of(row));
        when(outboxRepository.saveAll(anyList())).thenReturn(List.of(row));

        doThrow(new IllegalStateException("poison payload"))
                .when(dispatcher)
                .publish(row);

        OutboxPublishResult result = batchProcessor.publishNextBatch();

        assertThat(result).isEqualTo(new OutboxPublishResult(1, 0, 1, 1));
        assertThat(row.getRetryCount()).isEqualTo(3);
        assertThat(row.getDeadLetteredAt()).isNotNull();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getLastError()).contains("poison payload");
        verify(outboxRepository).saveAll(List.of(row));
    }

    @Test
    void publishNextBatch_ShouldReturnEmptyWhenNoRowsEligible() {
        when(outboxRepository.lockNextBatchForPublish(any(), anyInt())).thenReturn(List.of());

        OutboxPublishResult result = batchProcessor.publishNextBatch();

        assertThat(result).isEqualTo(OutboxPublishResult.EMPTY);
        verify(outboxRepository, never()).saveAll(anyList());
    }

    private ProfileEventOutbox pendingRow(ProfileOutboxEventType eventType) {
        return ProfileEventOutbox.pending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                eventType,
                "{\"event\":\"payload\"}",
                Instant.now()
        );
    }
}
