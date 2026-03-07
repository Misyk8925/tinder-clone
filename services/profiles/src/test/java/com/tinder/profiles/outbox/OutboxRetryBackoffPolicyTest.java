package com.tinder.profiles.outbox;

import com.tinder.profiles.config.OutboxPublisherProperties;
import com.tinder.profiles.outbox.model.OutboxRetryBackoffPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryBackoffPolicyTest {

    @Test
    void nextDelay_ShouldApplyExponentialBackoffAndCapAtMax() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setInitialBackoffMs(100);
        properties.setMaxBackoffMs(1000);
        properties.setBackoffMultiplier(2.0);

        OutboxRetryBackoffPolicy policy = new OutboxRetryBackoffPolicy(properties);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.nextDelay(1)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(800));
        assertThat(policy.nextDelay(4)).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.nextDelay(10)).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    void nextDelay_ShouldFallbackToLinearWhenMultiplierIsInvalid() {
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        properties.setInitialBackoffMs(250);
        properties.setMaxBackoffMs(1000);
        properties.setBackoffMultiplier(0.0);

        OutboxRetryBackoffPolicy policy = new OutboxRetryBackoffPolicy(properties);

        assertThat(policy.nextDelay(0)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.nextDelay(3)).isEqualTo(Duration.ofMillis(250));
    }
}
