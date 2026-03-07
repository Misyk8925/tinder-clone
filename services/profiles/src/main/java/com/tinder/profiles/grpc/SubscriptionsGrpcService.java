package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.ProfileApplicationService;
import com.tinder.profiles.user.KeycloakAdminClient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class SubscriptionsGrpcService extends SubscriptionsServiceGrpc.SubscriptionsServiceImplBase {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    private final ProfileApplicationService service;
    private final KeycloakAdminClient keycloakAdminClient;

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
            // 1. Mark premium in profiles DB (commits immediately)
            service.updatePremiumStatus(userId, true);

            // 2. Assign Keycloak role so the JWT reflects the new status
            //    Runs outside the DB transaction — safe to fail independently.
            //    If it throws, the gRPC error propagates to subscriptions which retries.
            keycloakAdminClient.assignRealmRole(userId, PREMIUM_ROLE);

            responseObserver.onNext(UpdatePremiumUserResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Failed to update premium status for user '{}': {}", userId, e.getMessage(), e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
