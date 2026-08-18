package com.tinder.profiles.application.photos.exception;

/** Thrown when an uploaded image or a requested slot violates the photo policy. */
public class PhotoValidationException extends PhotoException {

    public PhotoValidationException(String message) {
        super(message, "INVALID_IMAGE");
    }
}
