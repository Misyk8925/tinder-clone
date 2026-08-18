package com.tinder.profiles.application.photos.exception;

import java.util.UUID;

/** Thrown when a caller tries to act on a photo owned by another profile. */
public class PhotoAccessDeniedException extends PhotoException {

    public PhotoAccessDeniedException(UUID photoId) {
        super("Not authorized to modify photo '%s'".formatted(photoId), "PHOTO_FORBIDDEN");
    }
}
