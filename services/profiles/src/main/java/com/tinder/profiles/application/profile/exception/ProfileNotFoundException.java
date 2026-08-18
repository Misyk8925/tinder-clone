package com.tinder.profiles.application.profile.exception;

/**
 * Thrown when a profile cannot be found by ID or userId.
 */
public class ProfileNotFoundException extends ProfileException {

    public ProfileNotFoundException(String userId) {
        super(
            "Profile for userId '%s' not found".formatted(userId),
            "PROFILE_NOT_FOUND"
        );
    }

    public ProfileNotFoundException(String identifier, String identifierType) {
        super(
            "Profile with %s '%s' not found".formatted(identifierType, identifier),
            "PROFILE_NOT_FOUND"
        );
    }
}
