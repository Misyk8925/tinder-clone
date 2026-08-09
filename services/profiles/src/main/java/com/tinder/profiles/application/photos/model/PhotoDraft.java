package com.tinder.profiles.application.photos.model;

import java.util.UUID;

/** A photo about to be catalogued; the identity is assigned by the catalogue. */
public record PhotoDraft(
        UUID profileId,
        String s3Key,
        boolean primary,
        int position,
        String url,
        String contentType,
        long size
) {
}
