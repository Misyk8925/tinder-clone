package com.tinder.deckread.client;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * Recovers {@code photos/{profileId}/{storageId}/{variant}.jpg} from a stored
 * key or a CloudFront/S3 URL, including already-presigned URLs whose path is
 * unchanged.
 */
public final class PhotoObjectRef {

    public static final String NAMESPACE = "photos";

    private PhotoObjectRef() {
    }

    public record Ref(UUID ownerId, String storageId, String variant) {
    }

    public static Optional<Ref> parse(UUID fallbackOwnerId, String urlOrKey) {
        if (fallbackOwnerId == null || urlOrKey == null || urlOrKey.isBlank()) {
            return Optional.empty();
        }
        String path = urlOrKey.startsWith("http") ? pathOf(urlOrKey) : urlOrKey;
        if (path == null) {
            return Optional.empty();
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String[] parts = path.split("/");
        if (parts.length < 4 || !NAMESPACE.equals(parts[0])) {
            return Optional.empty();
        }
        String filename = parts[3];
        String variant = filename.endsWith(".jpg")
                ? filename.substring(0, filename.length() - 4)
                : filename;
        if (variant.isBlank()) {
            variant = "original";
        }
        return Optional.of(new Ref(fallbackOwnerId, parts[2], variant));
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
