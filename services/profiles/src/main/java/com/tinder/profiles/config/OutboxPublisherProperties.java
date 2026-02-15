package com.tinder.profiles.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for outbox background publisher.
 */
@Data
@ConfigurationProperties(prefix = "outbox.publisher")
public class OutboxPublisherProperties {

    private boolean enabled = true;

    private int batchSize = 50;

    private int maxBatchesPerRun = 5;

    private long pollIntervalMs = 1000;

    private long initialBackoffMs = 1000;

    private long maxBackoffMs = 60000;

    private double backoffMultiplier = 2.0;

    private long sendTimeoutMs = 5000;

    private int maxErrorLength = 1000;

    private int maxRetries = 10;
}
