package com.tinder.match.moderation;

public class ContentBlockedException extends RuntimeException {
    public ContentBlockedException(String message) {
        super(message);
    }
}
