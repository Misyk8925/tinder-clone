package com.tinder.profiles.api.scheduling;

import com.tinder.profiles.application.profile.usecase.PremiumMembershipService;
import com.tinder.profiles.application.profile.usecase.PremiumMembershipService.LapsedMembership;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The scheduled trigger only drives the use case: which memberships have lapsed
 * is decided by {@link PremiumMembershipService} (see its own test).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PremiumExpirationScheduler")
class PremiumExpirationSchedulerTest {

    @Mock private PremiumMembershipService premiumMembership;
    @Mock(lenient = true) private Tracer tracer;
    @Mock(lenient = true) private Span span;
    @Mock(lenient = true) private Tracer.SpanInScope spanInScope;

    private PremiumExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(any())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(spanInScope);
        scheduler = new PremiumExpirationScheduler(premiumMembership, tracer);
    }

    @Test
    @DisplayName("nothing lapsed means nothing revoked")
    void noExpiredSubscriptionsSkipsProcessing() {
        when(premiumMembership.findLapsed()).thenReturn(List.of());

        scheduler.revokeExpiredPremiumSubscriptions();

        verify(premiumMembership).findLapsed();
        verifyNoMoreInteractions(premiumMembership);
    }

    @Test
    @DisplayName("revokes every lapsed membership")
    void expiredProfilesAreRevoked() {
        when(premiumMembership.findLapsed()).thenReturn(List.of(
                new LapsedMembership("user-a", LocalDateTime.now().minusDays(1)),
                new LapsedMembership("user-b", LocalDateTime.now().minusHours(2))));

        scheduler.revokeExpiredPremiumSubscriptions();

        verify(premiumMembership).revoke("user-a");
        verify(premiumMembership).revoke("user-b");
    }

    @Test
    @DisplayName("one failing revocation does not stop the batch")
    void failureForOneProfileDoesNotStopTheBatch() {
        when(premiumMembership.findLapsed()).thenReturn(List.of(
                new LapsedMembership("user-fail", LocalDateTime.now().minusDays(1)),
                new LapsedMembership("user-ok", LocalDateTime.now().minusDays(1))));
        doThrow(new RuntimeException("failed")).when(premiumMembership).revoke("user-fail");

        scheduler.revokeExpiredPremiumSubscriptions();

        verify(premiumMembership).revoke("user-ok");
    }
}
