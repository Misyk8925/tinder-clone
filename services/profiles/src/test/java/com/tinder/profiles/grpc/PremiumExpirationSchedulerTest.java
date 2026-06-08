package com.tinder.profiles.grpc;
import com.tinder.profiles.infrastructure.messaging.scheduler.PremiumExpirationScheduler;

import com.tinder.profiles.infrastructure.persistence.profile.ProfileJpaEntity;
import com.tinder.profiles.application.profile.usecase.UpdatePremiumStatusService;
import com.tinder.profiles.application.profile.command.UpdatePremiumStatusCommand;
import com.tinder.profiles.infrastructure.persistence.profile.ProfileRepository;
import com.tinder.profiles.infrastructure.external.keycloak.KeycloakAdminClient;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PremiumExpirationSchedulerTest {

    private static final String PREMIUM_ROLE = "USER_PREMIUM";

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UpdatePremiumStatusService updatePremiumStatusUseCase;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock(lenient = true)
    private Tracer tracer;

    @Mock(lenient = true)
    private Span span;

    @Mock(lenient = true)
    private Tracer.SpanInScope spanInScope;

    private PremiumExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(any())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(spanInScope);
        scheduler = new PremiumExpirationScheduler(
                profileRepository,
                updatePremiumStatusUseCase,
                keycloakAdminClient,
                tracer
        );
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void revokeExpiredPremiumSubscriptions_noExpiredSubscriptions_skipsProcessing() {
        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.revokeExpiredPremiumSubscriptions();

        verifyNoInteractions(updatePremiumStatusUseCase, keycloakAdminClient);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_singleExpiredProfile_revokesDbAndKeycloak() {
        ProfileJpaEntity expired = buildExpiredProfile("user-123", LocalDateTime.now().minusMinutes(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        scheduler.revokeExpiredPremiumSubscriptions();

        // DB must be cleared before Keycloak call
        InOrder order = inOrder(updatePremiumStatusUseCase, keycloakAdminClient);
        order.verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-123", false, null));
        order.verify(keycloakAdminClient).removeRealmRole("user-123", PREMIUM_ROLE);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_multipleExpiredProfiles_revokesAll() {
        ProfileJpaEntity p1 = buildExpiredProfile("user-A", LocalDateTime.now().minusDays(1));
        ProfileJpaEntity p2 = buildExpiredProfile("user-B", LocalDateTime.now().minusHours(2));
        ProfileJpaEntity p3 = buildExpiredProfile("user-C", LocalDateTime.now().minusMinutes(5));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(p1, p2, p3));

        scheduler.revokeExpiredPremiumSubscriptions();

        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-A", false, null));
        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-B", false, null));
        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-C", false, null));

        verify(keycloakAdminClient).removeRealmRole("user-A", PREMIUM_ROLE);
        verify(keycloakAdminClient).removeRealmRole("user-B", PREMIUM_ROLE);
        verify(keycloakAdminClient).removeRealmRole("user-C", PREMIUM_ROLE);
    }

    // ── Resilience ────────────────────────────────────────────────────────────

    @Test
    void revokeExpiredPremiumSubscriptions_dbFailureForOneProfile_continuesWithOthers() {
        ProfileJpaEntity failing = buildExpiredProfile("user-FAIL", LocalDateTime.now().minusDays(2));
        ProfileJpaEntity ok      = buildExpiredProfile("user-OK",   LocalDateTime.now().minusDays(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(failing, ok));

        doThrow(new RuntimeException("DB connection lost"))
                .when(updatePremiumStatusUseCase)
                .handle(new UpdatePremiumStatusCommand("user-FAIL", false, null));

        // Should not throw, should process user-OK despite failure on user-FAIL
        scheduler.revokeExpiredPremiumSubscriptions();

        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-OK", false, null));
        verify(keycloakAdminClient).removeRealmRole("user-OK", PREMIUM_ROLE);

        // Keycloak must NOT be called for the failed user
        verify(keycloakAdminClient, never()).removeRealmRole("user-FAIL", PREMIUM_ROLE);
    }

    @Test
    void revokeExpiredPremiumSubscriptions_keycloakFailureForOneProfile_continuesWithOthers() {
        ProfileJpaEntity failing = buildExpiredProfile("user-KC-FAIL", LocalDateTime.now().minusDays(1));
        ProfileJpaEntity ok      = buildExpiredProfile("user-KC-OK",   LocalDateTime.now().minusDays(1));

        when(profileRepository.findAllByIsPremiumTrueAndPremiumExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(failing, ok));

        doThrow(new RuntimeException("Keycloak unreachable"))
                .when(keycloakAdminClient)
                .removeRealmRole(eq("user-KC-FAIL"), eq(PREMIUM_ROLE));

        // Should not throw
        scheduler.revokeExpiredPremiumSubscriptions();

        // Both DB updates are called
        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-KC-FAIL", false, null));
        verify(updatePremiumStatusUseCase).handle(new UpdatePremiumStatusCommand("user-KC-OK", false, null));

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

    private ProfileJpaEntity buildExpiredProfile(String userId, LocalDateTime expiresAt) {
        ProfileJpaEntity p = new ProfileJpaEntity();
        p.setUserId(userId);
        p.setPremium(true);
        p.setPremiumExpiresAt(expiresAt);
        return p;
    }
}


