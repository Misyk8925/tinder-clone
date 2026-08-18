package com.tinder.profiles.application.photos.model;

/** Result of a successful upload: the storage id and one URL per variant. */
public record UploadedPhoto(
        String photoId,
        String originalUrl,
        String largeUrl,
        String mediumUrl,
        String smallUrl
) {
}
