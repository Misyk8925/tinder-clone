package com.tinder.profiles.application.profile.command;

/** Application-layer intent to soft-delete the caller's profile. */
public record DeleteProfileCommand(String userId) {
}
