package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileApplicationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class SubscriptionsGrpcService extends SubscriptionsServiceGrpc.SubscriptionsServiceImplBase {

    private final ProfileApplicationService service;
    @Override
    public void updatePremiumUser(UpdatePremiumUserRequest request,
                                  StreamObserver<UpdatePremiumUserResponse> responseObserver) {
        log.info("Received gRPC request to update premium user");
        String userId = request.getUserId();
        log.info("Received gRPC request to update premium user: {}", userId);

        if (userId.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("User ID is required").asRuntimeException());
            return;
        }
        try{
            service.updatePremiumStatus(userId, true);
            UpdatePremiumUserResponse response = UpdatePremiumUserResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            // Send response back to the caller
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e){
            log.error("Failed to update premium status for user: {}", userId, e);

            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException()
            );
        }


    }



}

