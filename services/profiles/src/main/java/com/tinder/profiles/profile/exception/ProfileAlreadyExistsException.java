package com.tinder.profiles.profile.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting to create a profile for a user who already has one.
 */
public class ProfileAlreadyExistsException extends ProfileException {

    public ProfileAlreadyExistsException(String userId) {
        super(
            "Profile for userId '%s' already exists".formatted(userId),
            HttpStatus.CONFLICT,
            "PROFILE_ALREADY_EXISTS"
        );
    }
}

