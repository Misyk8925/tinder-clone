package com.tinder.profiles.application.profile.exception;

/**
 * Base exception for all profile-related errors.
 * Provides common error handling structure.
 */
public abstract class ProfileException extends RuntimeException {

    private final String errorCode;

    protected ProfileException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ProfileException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
