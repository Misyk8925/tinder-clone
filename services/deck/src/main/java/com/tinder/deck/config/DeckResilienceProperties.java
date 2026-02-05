package com.tinder.deck.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "deck.resilience")
public class DeckResilienceProperties {

    private Client profiles = new Client();
    private Client swipes = new Client();
    private Client redis = new Client();

    @Data
    public static class Client {
        private Duration timeout = Duration.ofMillis(1500);
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private Retry retry = new Retry();
        private Bulkhead bulkhead = new Bulkhead();
    }

    @Data
    public static class CircuitBreaker {
        private int slidingWindowSize = 50;
        private int minimumNumberOfCalls = 20;
        private int permittedNumberOfCallsInHalfOpenState = 5;
        private Duration waitDurationInOpenState = Duration.ofSeconds(20);
        private float failureRateThreshold = 50.0f;
        private float slowCallRateThreshold = 50.0f;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(1);
    }

    @Data
    public static class Retry {
        private int maxAttempts = 2;
        private Duration initialInterval = Duration.ofMillis(200);
        private double multiplier = 2.0;
        private double jitter = 0.5;
    }

    @Data
    public static class Bulkhead {
        private int maxConcurrentCalls = 50;
        private Duration maxWaitDuration = Duration.ZERO;
    }
}

