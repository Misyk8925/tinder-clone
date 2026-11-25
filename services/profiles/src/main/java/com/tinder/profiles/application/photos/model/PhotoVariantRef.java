package com.tinder.profiles.application.photos.model;

import java.util.UUID;

/**
 * One stored variant of a photo, as the photos service addresses it:
 * owner (the profile id), object folder ({@code storageId}) and size
 * ({@code original}, {@code large}, {@code medium}, {@code small}).
 */
public record PhotoVariantRef(UUID ownerId, String storageId, String variant) {
}
