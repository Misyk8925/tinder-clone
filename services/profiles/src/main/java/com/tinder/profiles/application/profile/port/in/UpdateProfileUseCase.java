package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.UpdateProfileCommand;

import java.util.UUID;

public interface UpdateProfileUseCase {
    /** Fully updates the profile and returns its id. */
    UUID handle(UpdateProfileCommand command);
}
