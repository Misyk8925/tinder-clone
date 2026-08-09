package com.tinder.profiles.application.photos.exception;

/**
 * Base exception for all photo-related errors, mirroring the profile feature's
 * hierarchy: every failure carries a stable error code for the API layer.
 */
public abstract class PhotoException extends RuntimeException {

    private final String errorCode;

    protected PhotoException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected PhotoException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
