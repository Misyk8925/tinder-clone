package com.tinder.profiles.application.photos.support;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

/**
 * The object-key layout of stored photos: {@code photos/{profileId}/{storageId}/{variant}.jpg}.
 *
 * <p>Kept in one place because both the upload path (writing variants) and the
 * orphan cleanup (comparing storage against the catalogue) need to agree on it.
 * The {@code storageId} is the id of the object folder and is deliberately
 * distinct from the catalogue's photo id.
 */
public final class PhotoKeys {

    /** Variant names, largest first; {@code original} is the catalogued one. */
    public static final List<String> VARIANTS = List.of("original", "large", "medium", "small");

    private static final String EXTENSION = ".jpg";

    private PhotoKeys() {
    }

    public static String profilePrefix(UUID profileId) {
        return "photos/%s/".formatted(profileId);
    }

    public static String baseKey(UUID profileId, String storageId) {
        return "photos/%s/%s".formatted(profileId, storageId);
    }

    public static String variantKey(UUID profileId, String storageId, String variant) {
        requireKnownVariant(variant);
        return baseKey(profileId, storageId) + "/" + variant + EXTENSION;
    }

    public static List<String> allVariantKeys(UUID profileId, String storageId) {
        return VARIANTS.stream().map(variant -> variantKey(profileId, storageId, variant)).toList();
    }

    public static void requireKnownVariant(String variant) {
        if (!VARIANTS.contains(variant)) {
            throw new PhotoValidationException("Unknown photo size: " + variant);
        }
    }

    /**
     * Recovers the storage id from a stored key or a public URL, tolerating the
     * CloudFront/S3 URL forms written by earlier versions of the service.
     */
    public static String storageIdOf(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isEmpty()) {
            throw new PhotoValidationException("S3 key or URL cannot be null or empty");
        }

        String path = keyOrUrl.startsWith("http") ? pathOf(keyOrUrl) : keyOrUrl;
        String[] parts = path.split("/");
        if (parts.length < 4 || !parts[0].equals("photos")) {
            throw new PhotoValidationException(
                    "Invalid S3 key format: " + path + ". Expected photos/{profileId}/{storageId}/{variant}.jpg");
        }
        return parts[2];
    }

    private static String pathOf(String url) {
        try {
            String path = new URL(url).getPath();
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (MalformedURLException e) {
            throw new PhotoValidationException("Invalid URL format: " + url);
        }
    }
}
