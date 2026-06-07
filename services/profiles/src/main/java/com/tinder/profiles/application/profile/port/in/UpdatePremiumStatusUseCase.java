package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.UpdatePremiumStatusCommand;

public interface UpdatePremiumStatusUseCase {
    void handle(UpdatePremiumStatusCommand command);
}
