package com.tinder.profiles.application.photos.exception;

import java.util.UUID;

/** Thrown when a photo does not exist in the catalogue. */
public class PhotoNotFoundException extends PhotoException {

    public PhotoNotFoundException(UUID photoId) {
        super("Photo '%s' not found".formatted(photoId), "PHOTO_NOT_FOUND");
    }
}
