package com.tinder.profiles.application.profile.usecase;

import com.tinder.profiles.application.profile.command.UpdatePremiumStatusCommand;
import com.tinder.profiles.application.profile.exception.ProfileNotFoundException;
import com.tinder.profiles.application.profile.port.out.ProfileRepositoryPort;
import com.tinder.profiles.domain.profile.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePremiumStatusService {

    private final ProfileRepositoryPort profiles;

    @Transactional
    public void handle(UpdatePremiumStatusCommand cmd) {
        Profile existing = profiles.findByUserId(cmd.userId())
                .orElseThrow(() -> new ProfileNotFoundException(cmd.userId()));
        existing.changePremium(cmd.premium(), cmd.expiresAt());
        profiles.save(existing);
    }
}
