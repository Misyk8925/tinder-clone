package com.tinder.subscriptions.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionGrpcClientCircuitBreakerTest {

    private SubscriptionsServiceGrpc.SubscriptionsServiceBlockingStub mockStub;
    private SubscriptionGrpcClient client;
    private CircuitBreaker circuitBreaker;

    // slidingWindowSize=3, threshold=50% => circuit opens after 2/3 failures (≥50%)
    private static final int WINDOW_SIZE = 3;

    @BeforeEach
    void setUp() throws Exception {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(WINDOW_SIZE)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("test-profiles-grpc");

        mockStub = mock(SubscriptionsServiceGrpc.SubscriptionsServiceBlockingStub.class);
        client = new SubscriptionGrpcClient(circuitBreaker);

        Field field = SubscriptionGrpcClient.class.getDeclaredField("subscriptionsServiceStub");
        field.setAccessible(true);
        field.set(client, mockStub);
    }

    @Test
    void successfulCallKeepsCircuitClosed() {
        when(mockStub.updatePremiumUser(any()))
                .thenReturn(UpdatePremiumUserResponse.newBuilder().setSuccess(true).build());

        UpdatePremiumUserResponse response = client.updatePremiumUser("user-1");

        assertThat(response.getSuccess()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitOpensAfterFailureThresholdExceeded() {
        when(mockStub.updatePremiumUser(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        // Fill the sliding window with failures (all 3 fail = 100% > 50% threshold)
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try { client.updatePremiumUser("user-" + i); } catch (StatusRuntimeException ignored) {}
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void circuitBlocksCallsWhenOpen() {
        when(mockStub.updatePremiumUser(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        // Trip the circuit open
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try { client.updatePremiumUser("user-" + i); } catch (StatusRuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call must be rejected by the circuit breaker without hitting the stub
        assertThatThrownBy(() -> client.updatePremiumUser("user-blocked"))
                .isInstanceOf(CallNotPermittedException.class);

        // Stub was called only for the window-filling attempts, not the blocked one
        verify(mockStub, times(WINDOW_SIZE)).updatePremiumUser(any());
    }

    @Test
    void circuitRemainsClosedBelowFailureThreshold() {
        UpdatePremiumUserResponse success = UpdatePremiumUserResponse.newBuilder().setSuccess(true).build();
        StatusRuntimeException failure = new StatusRuntimeException(Status.UNAVAILABLE);

        // 1 failure then 2 successes in a window of 3 = 33% failure rate < 50% threshold
        when(mockStub.updatePremiumUser(any()))
                .thenThrow(failure)
                .thenReturn(success)
                .thenReturn(success);

        try { client.updatePremiumUser("user-1"); } catch (StatusRuntimeException ignored) {}
        client.updatePremiumUser("user-2");
        client.updatePremiumUser("user-3");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitTransitionsToClosedAfterSuccessfulHalfOpenCall() throws InterruptedException {
        // Chain: 3 failures to trip the circuit, then success for the recovery call
        when(mockStub.updatePremiumUser(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE))
                .thenReturn(UpdatePremiumUserResponse.newBuilder().setSuccess(true).build());

        // Trip circuit open
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try { client.updatePremiumUser("user-" + i); } catch (StatusRuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for waitDurationInOpenState to expire → circuit moves to HALF_OPEN on next call
        Thread.sleep(150);

        // The first permitted call in HALF_OPEN state succeeds → circuit CLOSED
        UpdatePremiumUserResponse response = client.updatePremiumUser("user-recovery");

        assertThat(response.getSuccess()).isTrue();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void circuitReopensAfterFailureInHalfOpenState() throws InterruptedException {
        when(mockStub.updatePremiumUser(any()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        // Trip circuit open
        for (int i = 0; i < WINDOW_SIZE; i++) {
            try { client.updatePremiumUser("user-" + i); } catch (StatusRuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Wait for HALF_OPEN
        Thread.sleep(150);

        // The permitted call in HALF_OPEN also fails → circuit goes back to OPEN
        try { client.updatePremiumUser("user-halfopen"); } catch (StatusRuntimeException ignored) {}

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
