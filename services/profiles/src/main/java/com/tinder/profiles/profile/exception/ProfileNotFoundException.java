package com.tinder.profiles.profile.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a profile cannot be found by ID or userId.
 */
public class ProfileNotFoundException extends ProfileException {

    public ProfileNotFoundException(String userId) {
        super(
            "Profile for userId '%s' not found".formatted(userId),
            HttpStatus.NOT_FOUND,
            "PROFILE_NOT_FOUND"
        );
    }

    public ProfileNotFoundException(String identifier, String identifierType) {
        super(
            "Profile with %s '%s' not found".formatted(identifierType, identifier),
            HttpStatus.NOT_FOUND,
            "PROFILE_NOT_FOUND"
        );
    }
}

