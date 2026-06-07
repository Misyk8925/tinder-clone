package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.PatchProfileCommand;

import java.util.UUID;

public interface PatchProfileUseCase {
    /** Applies a partial update and returns the profile id. */
    UUID handle(PatchProfileCommand command);
}
