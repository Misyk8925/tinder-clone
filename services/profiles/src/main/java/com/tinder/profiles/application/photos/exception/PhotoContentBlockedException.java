package com.tinder.profiles.application.photos.exception;

/** Raised when moderation rejects an uploaded profile photo. */
public class PhotoContentBlockedException extends PhotoException {
    public PhotoContentBlockedException(String message) {
        super(message, "CONTENT_BLOCKED");
    }
}
