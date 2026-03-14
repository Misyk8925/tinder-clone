package com.tinder.profiles.grpc;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileApplicationService;
import com.tinder.profiles.profile.ProfileRepository;
import com.tinder.profiles.user.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PremiumExpirationSchedulerTest {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileApplicationService profileApplicationService;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private PremiumExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PremiumExpirationScheduler(
                profileRepository,
                profileApplicationService,
                keycloakAdminClient,
                null
        );
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void revokeExpiredPremiumSubscriptions_noExpiredSubscriptions_skipsProcessing() {
        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.revokeExpiredPremiumSubscriptions();

        verifyNoInteractions(profileApplicationService, keycloakAdminClient);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_singleExpiredProfile_revokesDbAndKeycloak() {
        Profile expired = buildExpiredProfile("user-123", LocalDateTime.now().minusMinutes(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        scheduler.revokeExpiredPremiumSubscriptions();

        // DB must be cleared before Keycloak call
        InOrder order = inOrder(profileApplicationService, keycloakAdminClient);
        order.verify(profileApplicationService).updatePremiumStatus("user-123", false);
        order.verify(keycloakAdminClient).removeRealmRole("user-123", PREMIUM_ROLE);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_multipleExpiredProfiles_revokesAll() {
        Profile p1 = buildExpiredProfile("user-A", LocalDateTime.now().minusDays(1));
        Profile p2 = buildExpiredProfile("user-B", LocalDateTime.now().minusHours(2));
        Profile p3 = buildExpiredProfile("user-C", LocalDateTime.now().minusMinutes(5));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(p1, p2, p3));

        scheduler.revokeExpiredPremiumSubscriptions();

        verify(profileApplicationService).updatePremiumStatus("user-A", false);
        verify(profileApplicationService).updatePremiumStatus("user-B", false);
        verify(profileApplicationService).updatePremiumStatus("user-C", false);

        verify(keycloakAdminClient).removeRealmRole("user-A", PREMIUM_ROLE);
        verify(keycloakAdminClient).removeRealmRole("user-B", PREMIUM_ROLE);
        verify(keycloakAdminClient).removeRealmRole("user-C", PREMIUM_ROLE);
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void revokeExpiredPremiumSubscriptions_dbFailureForOneProfile_continuesWithOthers() {
        Profile failing = buildExpiredProfile("user-FAIL", LocalDateTime.now().minusDays(2));
        Profile ok      = buildExpiredProfile("user-OK",   LocalDateTime.now().minusDays(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(failing, ok));

        doThrow(new RuntimeException("DB connection lost"))
                .when(profileApplicationService)
                .updatePremiumStatus("user-FAIL", false);

        // Should not throw, should process user-OK despite failure on user-FAIL
        scheduler.revokeExpiredPremiumSubscriptions();

        verify(profileApplicationService).updatePremiumStatus("user-OK", false);
        verify(keycloakAdminClient).removeRealmRole("user-OK", PREMIUM_ROLE);

        // Keycloak must NOT be called for the failed user
        verify(keycloakAdminClient, never()).removeRealmRole("user-FAIL", PREMIUM_ROLE);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_keycloakFailureForOneProfile_continuesWithOthers() {
        Profile failing = buildExpiredProfile("user-KC-FAIL", LocalDateTime.now().minusDays(1));
        Profile ok      = buildExpiredProfile("user-KC-OK",   LocalDateTime.now().minusDays(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(failing, ok));

        doThrow(new RuntimeException("Keycloak unreachable"))
                .when(keycloakAdminClient)
                .removeRealmRole(eq("user-KC-FAIL"), eq(PREMIUM_ROLE));

        // Should not throw
        scheduler.revokeExpiredPremiumSubscriptions();

        // Both DB updates are called
        verify(profileApplicationService).updatePremiumStatus("user-KC-FAIL", false);
        verify(profileApplicationService).updatePremiumStatus("user-KC-OK",   false);

        // Keycloak succeeds for the second user even though the first failed
        verify(keycloakAdminClient).removeRealmRole("user-KC-OK", PREMIUM_ROLE);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_passesCurrentTimestampToRepository() {
        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        scheduler.revokeExpiredPremiumSubscriptions();
        LocalDateTime after = LocalDateTime.now();

        // Capture the argument and verify it is between before and after (i.e. "now")
        verify(profileRepository).findAllByIsPremiumTrueAndPremiumExpiresAtBefore(
                argThat(ts -> !ts.isBefore(before) && !ts.isAfter(after))
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Profile buildExpiredProfile(String userId, LocalDateTime expiresAt) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setPremium(true);
        p.setPremiumExpiresAt(expiresAt);
        return p;
    }
}


