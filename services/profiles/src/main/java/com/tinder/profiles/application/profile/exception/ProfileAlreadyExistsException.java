package com.tinder.profiles.application.profile.exception;

/**
 * Thrown when attempting to create a profile for a user who already has one.
 */
public class ProfileAlreadyExistsException extends ProfileException {

    public ProfileAlreadyExistsException(String userId) {
        super(
            "Profile for userId '%s' already exists".formatted(userId),
            "PROFILE_ALREADY_EXISTS"
        );
    }
}
