package com.tinder.profiles.api.grpc;

import com.tinder.profiles.application.profile.usecase.PremiumMembershipService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SubscriptionsGrpcServiceTest {

    @Mock private PremiumMembershipService premiumMembership;
    @Mock private StreamObserver<UpdatePremiumUserResponse> responseObserver;

    private SubscriptionsGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new SubscriptionsGrpcService(premiumMembership);
    }

    @Test
    void validRequestActivatesPremiumAndReturnsSuccess() {
        Instant paidThrough = Instant.parse("2030-02-03T04:05:06Z");

        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder()
                        .setUserId("user-abc")
                        .setPremiumStatus(PremiumStatus.PREMIUM_STATUS_ACTIVE)
                        .setPremiumUntilEpochSeconds(paidThrough.getEpochSecond())
                        .build(),
                responseObserver);

        verify(premiumMembership).activate(
                "user-abc", LocalDateTime.ofInstant(paidThrough, ZoneOffset.UTC));
        verify(responseObserver).onNext(
                org.mockito.ArgumentMatchers.argThat(UpdatePremiumUserResponse::getSuccess));
        verify(responseObserver).onCompleted();
    }

    @Test
    void inactiveRequestRevokesPremiumAndReturnsSuccess() {
        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder()
                        .setUserId("user-abc")
                        .setPremiumStatus(PremiumStatus.PREMIUM_STATUS_INACTIVE)
                        .build(),
                responseObserver);

        verify(premiumMembership).revoke("user-abc");
        verify(responseObserver).onNext(
                org.mockito.ArgumentMatchers.argThat(UpdatePremiumUserResponse::getSuccess));
        verify(responseObserver).onCompleted();
    }

    @Test
    void activeRequestWithoutExpiryReturnsInvalidArgument() {
        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder()
                        .setUserId("user-abc")
                        .setPremiumStatus(PremiumStatus.PREMIUM_STATUS_ACTIVE)
                        .build(),
                responseObserver);

        ArgumentCaptor<StatusRuntimeException> error = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(error.capture());
        assertThat(error.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INVALID_ARGUMENT.getCode());
        verifyNoInteractions(premiumMembership);
    }

    @Test
    void blankUserIdReturnsInvalidArgument() {
        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder().setUserId("  ").build(),
                responseObserver);

        ArgumentCaptor<StatusRuntimeException> error = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(error.capture());
        assertThat(error.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INVALID_ARGUMENT.getCode());
        verifyNoInteractions(premiumMembership);
    }

    @Test
    void membershipFailureReturnsInternalErrorWithoutSuccess() {
        doThrow(new RuntimeException("identity provider unavailable"))
                .when(premiumMembership).activate(eq("user-xyz"), any());

        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder().setUserId("user-xyz").build(),
                responseObserver);

        ArgumentCaptor<StatusRuntimeException> error = ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(error.capture());
        assertThat(error.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INTERNAL.getCode());
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }
}
