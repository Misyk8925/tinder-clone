package com.tinder.profiles.api.grpc;

import com.tinder.profiles.application.profile.usecase.PremiumMembershipService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class SubscriptionsGrpcService extends SubscriptionsServiceGrpc.SubscriptionsServiceImplBase {

    private static final int PREMIUM_DURATION_DAYS = 30;

    private final PremiumMembershipService premiumMembership;

    @Override
    public void updatePremiumUser(UpdatePremiumUserRequest request,
                                  StreamObserver<UpdatePremiumUserResponse> responseObserver) {
        String userId = request.getUserId();
        log.info("Received gRPC request to update premium user: {}", userId);

        if (userId.isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription("User ID is required").asRuntimeException());
            return;
        }

        try {
            switch (request.getPremiumStatus()) {
                case PREMIUM_STATUS_ACTIVE -> activatePremium(userId, request);
                case PREMIUM_STATUS_INACTIVE -> {
                    premiumMembership.revoke(userId);
                    log.info("Premium revoked for user '{}'", userId);
                }
                case PREMIUM_STATUS_UNSPECIFIED, UNRECOGNIZED -> {
                    // Rolling-deploy compatibility for the original user_id-only request.
                    LocalDateTime expiresAt = LocalDateTime.now().plusDays(PREMIUM_DURATION_DAYS);
                    premiumMembership.activate(userId, expiresAt);
                    log.info("Premium activated for legacy request user '{}' until {}", userId, expiresAt);
                }
            }

            responseObserver.onNext(UpdatePremiumUserResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();

        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("Failed to update premium status for user '{}': {}", userId, e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private void activatePremium(String userId, UpdatePremiumUserRequest request) {
        if (request.getPremiumUntilEpochSeconds() <= 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Premium expiry is required for active status")
                    .asRuntimeException();
        }
        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(request.getPremiumUntilEpochSeconds()), ZoneOffset.UTC);
        premiumMembership.activate(userId, expiresAt);
        log.info("Premium activated for user '{}' until {}", userId, expiresAt);
    }
}
