package com.tinder.profiles.application.profile.port.in;

import com.tinder.profiles.application.profile.command.DeleteProfilesCommand;

public interface DeleteProfilesUseCase {
    void handle(DeleteProfilesCommand command);
}
