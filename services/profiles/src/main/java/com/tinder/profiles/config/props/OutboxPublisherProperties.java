package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for the background outbox publisher ({@code outbox.publisher.*}). */
@ConfigurationProperties(prefix = "outbox.publisher")
public record OutboxPublisherProperties(

        @DefaultValue("true") boolean enabled,

        @DefaultValue("50") int batchSize,

        @DefaultValue("5") int maxBatchesPerRun,

        @DefaultValue("1000") long pollIntervalMs,

        @DefaultValue("1000") long initialBackoffMs,

        @DefaultValue("60000") long maxBackoffMs,

        @DefaultValue("2.0") double backoffMultiplier,

        @DefaultValue("5000") long sendTimeoutMs,

        @DefaultValue("1000") int maxErrorLength,

        @DefaultValue("10") int maxRetries
) {
}
