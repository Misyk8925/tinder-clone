package com.tinder.profiles.profile.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a profile operation fails due to business rules.
 * For example: trying to delete an already deleted profile,
 * trying to activate a deleted profile, etc.
 */
public class ProfileOperationException extends ProfileException {

    public ProfileOperationException(String message) {
        super(
            message,
            HttpStatus.CONFLICT,
            "PROFILE_OPERATION_ERROR"
        );
    }

    public ProfileOperationException(String operation, String reason) {
        super(
            "Cannot perform operation '%s': %s".formatted(operation, reason),
            HttpStatus.CONFLICT,
            "PROFILE_OPERATION_ERROR"
        );
    }
}

