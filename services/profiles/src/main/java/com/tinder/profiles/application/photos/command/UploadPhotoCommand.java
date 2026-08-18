package com.tinder.profiles.application.photos.command;

/**
 * Application-layer intent to put an image into one of a profile's photo slots.
 * The bytes are already read by the inbound adapter, so no transport type leaks in.
 */
public record UploadPhotoCommand(
        String userId,
        byte[] image,
        String contentType,
        long size,
        int position
) {
}
