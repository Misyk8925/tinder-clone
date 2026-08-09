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

import java.time.LocalDateTime;

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
        LocalDateTime before = LocalDateTime.now().plusDays(30).minusSeconds(5);

        grpcService.updatePremiumUser(
                UpdatePremiumUserRequest.newBuilder().setUserId("user-abc").build(),
                responseObserver);

        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(premiumMembership).activate(eq("user-abc"), expiry.capture());
        assertThat(expiry.getValue()).isAfterOrEqualTo(before);
        verify(responseObserver).onNext(
                org.mockito.ArgumentMatchers.argThat(UpdatePremiumUserResponse::getSuccess));
        verify(responseObserver).onCompleted();
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
