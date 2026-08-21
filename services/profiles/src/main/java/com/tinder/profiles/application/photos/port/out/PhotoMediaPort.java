package com.tinder.profiles.application.photos.port.out;

import com.tinder.profiles.application.photos.model.StoredPhotoMedia;

import java.util.Collection;
import java.util.UUID;

/**
 * Outbound port for the photos service: validate, render variants and store
 * objects. Catalogue, slots and ownership stay in this service.
 */
public interface PhotoMediaPort {

    StoredPhotoMedia store(UUID ownerId, byte[] image, String contentType);

    void delete(UUID ownerId, String storageId);

    String presignedDownloadUrl(UUID ownerId, String storageId, String variant);

    int cleanupOrphans(UUID ownerId, Collection<String> cataloguedStorageIds);
}
