package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.UpdatePremiumStatusCommand;
import com.tinder.profiles.application.profile.port.out.PremiumRolePort;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Coordinates the transactional profile update with the separate identity
 * provider call. The inner status service retains the database transaction.
 *
 * <p>Also owns the rule for when a membership has lapsed, so the scheduled
 * trigger does not need to query the database itself.
 */
@Service
@RequiredArgsConstructor
public class PremiumMembershipService {

    private final UpdatePremiumStatusService updatePremiumStatus;
    private final PremiumRolePort premiumRoles;
    private final ProfileRepositoryPort profiles;

    public void activate(String userId, LocalDateTime expiresAt) {
        updatePremiumStatus.handle(new UpdatePremiumStatusCommand(userId, true, expiresAt));
        premiumRoles.grantPremium(userId);
    }

    public void revoke(String userId) {
        updatePremiumStatus.handle(new UpdatePremiumStatusCommand(userId, false, null));
        premiumRoles.revokePremium(userId);
    }

    /** Memberships whose paid period has ended while still marked premium. */
    public List<LapsedMembership> findLapsed() {
        return profiles.findExpiredPremium(LocalDateTime.now()).stream()
                .map(profile -> new LapsedMembership(profile.getUserId(), profile.getPremiumExpiresAt()))
                .toList();
    }

    public record LapsedMembership(String userId, LocalDateTime expiredAt) {
    }
}
