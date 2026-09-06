package com.tinder.profiles.application.profile.exception;

/** Raised when moderation rejects user-authored profile text. */
public class ContentBlockedException extends ProfileException {
    public ContentBlockedException(String message) {
        super(message, "CONTENT_BLOCKED");
    }
}
