package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.DeleteProfileCommand;

public interface DeleteProfileUseCase {
    void handle(DeleteProfileCommand command);
}
