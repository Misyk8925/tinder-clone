package com.tinder.clone.consumer.outbox;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublishErrorsTest {

    @Test
    void givenProducerConstructionFailureWhenSummarizedThenIncludesNestedConfigException() {
        Throwable error = new KafkaException(
                "Failed to construct kafka producer",
                new ConfigException("delivery.timeout.ms should be equal to or larger than linger.ms + request.timeout.ms")
        );

        assertThat(OutboxPublishErrors.summarize(error, 1000))
                .contains("Failed to construct kafka producer")
                .contains("delivery.timeout.ms should be equal to or larger than linger.ms + request.timeout.ms");
    }
}
