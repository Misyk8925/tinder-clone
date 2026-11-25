package com.tinder.profiles.infrastructure.photos;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns catalogued photos into short-lived URLs the browser can load.
 *
 * <p>The photos service owns the storage backend (S3 or local disk) and is the
 * only place that can mint a working download URL, so signing is delegated to it
 * in one batch call per read. Stored {@code photo.url} values stay as durable
 * locators; this is applied only when mapping photos for a client. When a photo
 * cannot be signed (legacy key, photos service down), its stored URL is returned
 * so a read never fails because of signing.
 */
@Component
@Slf4j
public class PhotoDownloadUrlSigner {

    /** What the catalogue knows about one photo: owner, object key and stored URL. */
    public record PhotoLocator(UUID profileId, String s3Key, String storedUrl) {
    }

    private final PhotoMediaPort media;

    public PhotoDownloadUrlSigner(PhotoMediaPort media) {
        this.media = media;
    }

    /**
     * Resolves a client URL for every locator with at most one call to the photos
     * service. Every input locator has an entry in the result.
     */
    public Map<PhotoLocator, String> forClient(Collection<PhotoLocator> photos) {
        if (photos == null || photos.isEmpty()) {
            return Map.of();
        }

        Map<PhotoLocator, PhotoVariantRef> refs = new HashMap<>(photos.size());
        for (PhotoLocator photo : photos) {
            PhotoVariantRef ref = variantRefOf(photo);
            if (ref != null) {
                refs.put(photo, ref);
            }
        }

        Map<PhotoVariantRef, String> signed = sign(new LinkedHashSet<>(refs.values()));

        Map<PhotoLocator, String> urls = new HashMap<>(photos.size());
        for (PhotoLocator photo : photos) {
            PhotoVariantRef ref = refs.get(photo);
            String url = ref == null ? null : signed.get(ref);
            urls.put(photo, url != null ? url : fallbackOf(photo));
        }
        return urls;
    }

    private Map<PhotoVariantRef, String> sign(Set<PhotoVariantRef> refs) {
        if (refs.isEmpty()) {
            return Map.of();
        }
        try {
            return media.presignedDownloadUrls(refs);
        } catch (RuntimeException failed) {
            log.warn("Failed to sign {} photo url(s) through the photos service; using stored urls: {}",
                    refs.size(), failed.getMessage());
            return Map.of();
        }
    }

    private static PhotoVariantRef variantRefOf(PhotoLocator photo) {
        if (photo.profileId() == null) {
            return null;
        }
        String locator = (photo.s3Key() != null && !photo.s3Key().isBlank()) ? photo.s3Key() : photo.storedUrl();
        if (locator == null || locator.isBlank()) {
            return null;
        }
        try {
            return new PhotoVariantRef(
                    photo.profileId(),
                    PhotoKeys.storageIdOf(locator),
                    PhotoKeys.variantOf(locator));
        } catch (PhotoValidationException notCatalogued) {
            log.debug("Photo locator is not a catalogued key; using stored url for profile {}", photo.profileId());
            return null;
        }
    }

    private static String fallbackOf(PhotoLocator photo) {
        return photo.storedUrl() == null ? photo.s3Key() : photo.storedUrl();
    }
}
