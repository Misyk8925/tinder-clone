package com.tinder.profiles.infrastructure.messaging.outbox.model;

import com.tinder.profiles.config.props.OutboxPublisherProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OutboxRetryBackoffPolicy {

    private final OutboxPublisherProperties properties;

    public Duration nextDelay(int currentRetryCount) {
        long initialBackoffMs = Math.max(0L, properties.initialBackoffMs());
        long maxBackoffMs = Math.max(initialBackoffMs, properties.maxBackoffMs());
        double multiplier = properties.backoffMultiplier() <= 0 ? 1.0 : properties.backoffMultiplier();

        double rawDelay = initialBackoffMs * Math.pow(multiplier, Math.max(0, currentRetryCount));
        long boundedDelay = Double.isFinite(rawDelay)
                ? Math.min((long) rawDelay, maxBackoffMs)
                : maxBackoffMs;

        return Duration.ofMillis(Math.max(0L, boundedDelay));
    }
}
