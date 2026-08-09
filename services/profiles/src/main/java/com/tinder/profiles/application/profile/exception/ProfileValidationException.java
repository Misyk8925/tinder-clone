package com.tinder.profiles.application.profile.exception;

/**
 * Thrown when profile data fails business validation rules.
 * This is for domain-level validation, not Bean Validation.
 */
public class ProfileValidationException extends ProfileException {

    public ProfileValidationException(String message) {
        super(
            message,
            "PROFILE_VALIDATION_ERROR"
        );
    }

    public ProfileValidationException(String field, String reason) {
        super(
            "Validation failed for field '%s': %s".formatted(field, reason),
            "PROFILE_VALIDATION_ERROR"
        );
    }
}
