package com.tinder.subscriptions.grpc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubscriptionGrpcClient {

    @GrpcClient("profiles-service")
    private SubscriptionsServiceGrpc.SubscriptionsServiceBlockingStub subscriptionsServiceStub;

    private final CircuitBreaker circuitBreaker;

    public SubscriptionGrpcClient(CircuitBreaker profilesGrpcCircuitBreaker) {
        this.circuitBreaker = profilesGrpcCircuitBreaker;
    }

    public UpdatePremiumUserResponse updatePremiumUser(String userId) {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId(userId)
                .build();
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return subscriptionsServiceStub.updatePremiumUser(request);
                } catch (StatusRuntimeException e) {
                    log.error("gRPC call updatePremiumUser failed for userId={}, status={}", userId, e.getStatus(), e);
                    throw e;
                }
            });
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker OPEN for profiles-grpc — call blocked for userId={}", userId);
            throw e;
        }
    }
}
