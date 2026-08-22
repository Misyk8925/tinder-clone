package com.tinder.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleBasedRateLimitFilterTest {

    @Test
    @DisplayName("Scenario: Given an authenticated user without USER_BASIC, when rate role is resolved, then basic limits apply")
    void authenticatedUsersWithoutBasicRoleUseBasicLimits() {
        SecurityService security = mock(SecurityService.class);
        when(security.isAdmin()).thenReturn(Mono.just(false));
        when(security.isPremiumUser()).thenReturn(Mono.just(false));

        assertThat(RoleBasedRateLimitFilter.resolveRole(security, true).block()).isEqualTo("basic");
    }

    @Test
    @DisplayName("Scenario: Given unauthenticated traffic, when rate role is resolved, then anon limits apply")
    void unauthenticatedTrafficUsesAnonLimits() {
        SecurityService security = mock(SecurityService.class);

        assertThat(RoleBasedRateLimitFilter.resolveRole(security, false).block()).isEqualTo("anon");
    }

    @Test
    @DisplayName("Scenario: Given a premium user, when rate role is resolved, then premium limits apply")
    void premiumUsersUsePremiumLimits() {
        SecurityService security = mock(SecurityService.class);
        when(security.isAdmin()).thenReturn(Mono.just(false));
        when(security.isPremiumUser()).thenReturn(Mono.just(true));

        assertThat(RoleBasedRateLimitFilter.resolveRole(security, true).block()).isEqualTo("premium");
    }
}
