package com.tinder.profiles.application.photos.port.out;

import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import com.tinder.profiles.application.photos.model.StoredPhotoMedia;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port for the photos service: validate, render variants and store
 * objects. Catalogue, slots and ownership stay in this service.
 */
public interface PhotoMediaPort {

    StoredPhotoMedia store(UUID ownerId, byte[] image, String contentType);

    void delete(UUID ownerId, String storageId);

    String presignedDownloadUrl(UUID ownerId, String storageId, String variant);

    /**
     * Signs many variants in one round trip. The photos service owns the storage
     * backend (S3 or local disk), so it is the only place that can mint a URL the
     * browser will actually load. Refs the service does not return are absent
     * from the map.
     */
    Map<PhotoVariantRef, String> presignedDownloadUrls(Collection<PhotoVariantRef> refs);

    int cleanupOrphans(UUID ownerId, Collection<String> cataloguedStorageIds);
}
