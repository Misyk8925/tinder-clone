package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.ProfileApplicationService;
import com.tinder.profiles.user.KeycloakAdminClient;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionsGrpcServiceTest {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    @Mock
    private ProfileApplicationService profileApplicationService;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private StreamObserver<UpdatePremiumUserResponse> responseObserver;

    private SubscriptionsGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new SubscriptionsGrpcService(profileApplicationService, keycloakAdminClient);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void updatePremiumUser_validRequest_completesThenReturnsSuccess() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("user-abc")
                .build();

        grpcService.updatePremiumUser(request, responseObserver);

        verify(responseObserver).onNext(
                argThat(r -> r.getSuccess())
        );
        verify(responseObserver).onCompleted();
        verifyNoMoreInteractions(responseObserver);
    }

    @Test
    void updatePremiumUser_validRequest_setsPremiumWithApprox30DayExpiry() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("user-abc")
                .build();

        LocalDateTime before = LocalDateTime.now().plusDays(30).minusSeconds(5);
        grpcService.updatePremiumUser(request, responseObserver);
        LocalDateTime after = LocalDateTime.now().plusDays(30).plusSeconds(5);

        // Capture the expiresAt argument passed to updatePremiumStatus
        ArgumentCaptor<LocalDateTime> expiryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(profileApplicationService)
                .updatePremiumStatus(eq("user-abc"), eq(true), expiryCaptor.capture());

        LocalDateTime captured = expiryCaptor.getValue();
        assertThat(captured).isAfterOrEqualTo(before);
        assertThat(captured).isBeforeOrEqualTo(after);
    }

    @Test
    void updatePremiumUser_validRequest_assignsKeycloakRoleAfterDbUpdate() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("user-abc")
                .build();

        grpcService.updatePremiumUser(request, responseObserver);

        // DB update must be first, then Keycloak
        var order = inOrder(profileApplicationService, keycloakAdminClient);
        order.verify(profileApplicationService)
                .updatePremiumStatus(eq("user-abc"), eq(true), any(LocalDateTime.class));
        order.verify(keycloakAdminClient).assignRealmRole("user-abc", PREMIUM_ROLE);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void updatePremiumUser_blankUserId_returnsInvalidArgumentError() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("  ")
                .build();

        grpcService.updatePremiumUser(request, responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        assertThat(errorCaptor.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INVALID_ARGUMENT.getCode());

        verifyNoInteractions(profileApplicationService, keycloakAdminClient);
    }

    @Test
    void updatePremiumUser_emptyUserId_returnsInvalidArgumentError() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("")
                .build();

        grpcService.updatePremiumUser(request, responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        assertThat(errorCaptor.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INVALID_ARGUMENT.getCode());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void updatePremiumUser_dbThrows_propagatesInternalError() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("user-xyz")
                .build();

        doThrow(new RuntimeException("DB is down"))
                .when(profileApplicationService)
                .updatePremiumStatus(any(), anyBoolean(), any());

        grpcService.updatePremiumUser(request, responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        assertThat(errorCaptor.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INTERNAL.getCode());
        assertThat(errorCaptor.getValue().getStatus().getDescription())
                .contains("DB is down");

        // No success response emitted
        verify(responseObserver, never()).onNext(any());
        verify(responseObserver, never()).onCompleted();
    }

    @Test
    void updatePremiumUser_keycloakThrows_propagatesInternalErrorAndNoSuccessEmitted() {
        UpdatePremiumUserRequest request = UpdatePremiumUserRequest.newBuilder()
                .setUserId("user-xyz")
                .build();

        doThrow(new RuntimeException("Keycloak unreachable"))
                .when(keycloakAdminClient)
                .assignRealmRole(any(), any());

        grpcService.updatePremiumUser(request, responseObserver);

        ArgumentCaptor<StatusRuntimeException> errorCaptor =
                ArgumentCaptor.forClass(StatusRuntimeException.class);
        verify(responseObserver).onError(errorCaptor.capture());

        assertThat(errorCaptor.getValue().getStatus().getCode())
                .isEqualTo(io.grpc.Status.INTERNAL.getCode());

        verify(responseObserver, never()).onNext(any());
    }
}

