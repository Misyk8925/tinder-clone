package com.tinder.profiles.application.photos.port.out;

import java.util.List;

/**
 * Outbound port for the object store holding photo bytes. Keys are built by the
 * application ({@code support.PhotoKeys}); the adapter only moves bytes.
 */
public interface PhotoStoragePort {

    void put(String key, byte[] data, String contentType);

    /** Best-effort delete: a missing object is not an error. */
    void delete(String key);

    List<String> listKeys(String prefix);

    /** Publicly reachable URL (CDN or bucket URL) for an object. */
    String publicUrl(String key);

    /** Time-limited download URL for a private object. */
    String presignedDownloadUrl(String key);
}
