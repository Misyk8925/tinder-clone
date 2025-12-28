package com.tinder.profiles.profile.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when profile data fails business validation rules.
 * This is for domain-level validation, not Bean Validation.
 */
public class ProfileValidationException extends ProfileException {

    public ProfileValidationException(String message) {
        super(
            message,
            HttpStatus.BAD_REQUEST,
            "PROFILE_VALIDATION_ERROR"
        );
    }

    public ProfileValidationException(String field, String reason) {
        super(
            "Validation failed for field '%s': %s".formatted(field, reason),
            HttpStatus.BAD_REQUEST,
            "PROFILE_VALIDATION_ERROR"
        );
    }
}

