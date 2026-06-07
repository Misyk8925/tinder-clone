package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.CreateProfileCommand;

import java.util.UUID;

public interface CreateProfileUseCase {
    /** Creates a profile and returns its id. */
    UUID handle(CreateProfileCommand command);
}
