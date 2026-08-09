package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.UpdatePremiumStatusCommand;
import com.tinder.profiles.application.profile.port.out.PremiumRolePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PremiumMembershipServiceTest {

    @Mock private UpdatePremiumStatusService statusService;
    @Mock private PremiumRolePort premiumRoles;
    @Mock private ProfileRepositoryPort profiles;

    private PremiumMembershipService service;

    @BeforeEach
    void setUp() {
        service = new PremiumMembershipService(statusService, premiumRoles, profiles);
    }

    @Test
    void activateCommitsStatusBeforeGrantingTheRole() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        service.activate("user-1", expiresAt);

        InOrder order = inOrder(statusService, premiumRoles);
        order.verify(statusService).handle(new UpdatePremiumStatusCommand("user-1", true, expiresAt));
        order.verify(premiumRoles).grantPremium("user-1");
    }

    @Test
    void revokeCommitsStatusBeforeRevokingTheRole() {
        service.revoke("user-1");

        InOrder order = inOrder(statusService, premiumRoles);
        order.verify(statusService).handle(new UpdatePremiumStatusCommand("user-1", false, null));
        order.verify(premiumRoles).revokePremium("user-1");
    }

    @Test
    void failedStatusUpdateDoesNotCallTheIdentityProvider() {
        UpdatePremiumStatusCommand command = new UpdatePremiumStatusCommand("user-1", false, null);
        doThrow(new RuntimeException("database unavailable")).when(statusService).handle(command);

        try {
            service.revoke("user-1");
        } catch (RuntimeException ignored) {
        }

        verify(premiumRoles, never()).revokePremium("user-1");
    }

    @Test
    void lapsedMembershipsAreThoseExpiredAsOfNow() {
        Profile expired = Profile.builder()
                .userId("user-expired")
                .premium(true)
                .premiumExpiresAt(LocalDateTime.now().minusDays(1))
                .build();
        given(profiles.findExpiredPremium(argThat(asOf -> !asOf.isAfter(LocalDateTime.now()))))
                .willReturn(java.util.List.of(expired));

        var lapsed = service.findLapsed();

        then(lapsed).singleElement()
                .extracting(PremiumMembershipService.LapsedMembership::userId)
                .isEqualTo("user-expired");
    }
}
