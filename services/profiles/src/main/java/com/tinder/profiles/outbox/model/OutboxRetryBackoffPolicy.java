package com.tinder.profiles.outbox.model;

import com.tinder.profiles.config.OutboxPublisherProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OutboxRetryBackoffPolicy {

    private final OutboxPublisherProperties properties;

    public Duration nextDelay(int currentRetryCount) {
        long initialBackoffMs = Math.max(0L, properties.getInitialBackoffMs());
        long maxBackoffMs = Math.max(initialBackoffMs, properties.getMaxBackoffMs());
        double multiplier = properties.getBackoffMultiplier() <= 0 ? 1.0 : properties.getBackoffMultiplier();

        double rawDelay = initialBackoffMs * Math.pow(multiplier, Math.max(0, currentRetryCount));
        long boundedDelay = Double.isFinite(rawDelay)
                ? Math.min((long) rawDelay, maxBackoffMs)
                : maxBackoffMs;

        return Duration.ofMillis(Math.max(0L, boundedDelay));
    }
}
