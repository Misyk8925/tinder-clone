package com.tinder.profiles.application.photos.exception;

/** Thrown when the storage backend fails on a write the caller depends on. */
public class PhotoStorageException extends PhotoException {

    public PhotoStorageException(String message, Throwable cause) {
        super(message, cause, "PHOTO_STORAGE_ERROR");
    }
}
