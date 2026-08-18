package com.tinder.profiles.application.profile.command;

import java.util.List;
import java.util.UUID;

/** Application-layer intent to hard-delete a batch of profiles by id. */
public record DeleteProfilesCommand(List<UUID> ids) {
}
