package com.tinder.profiles.profile.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all profile-related errors.
 * Provides common error handling structure.
 */
@Getter
public abstract class ProfileException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ProfileException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ProfileException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }
}
