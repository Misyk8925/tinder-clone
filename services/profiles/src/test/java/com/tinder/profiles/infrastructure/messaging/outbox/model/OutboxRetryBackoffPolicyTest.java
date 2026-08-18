package com.tinder.profiles.infrastructure.messaging.outbox.model;

import com.tinder.profiles.config.props.OutboxPublisherProperties;
import com.tinder.profiles.infrastructure.messaging.outbox.model.OutboxRetryBackoffPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryBackoffPolicyTest {

    @Test
    void nextDelay_ShouldApplyExponentialBackoffAndCapAtMax() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties(
                true, 50, 5, 1000L, 100L, 1000L, 2.0, 5000L, 1000, 10);

        OutboxRetryBackoffPolicy policy = new OutboxRetryBackoffPolicy(properties);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.nextDelay(4)).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.nextDelay(10)).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    void nextDelay_ShouldFallbackToLinearWhenMultiplierIsInvalid() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties(
                true, 50, 5, 1000L, 250L, 1000L, 0.0, 5000L, 1000, 10);

        OutboxRetryBackoffPolicy policy = new OutboxRetryBackoffPolicy(properties);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(250));
    }
}
