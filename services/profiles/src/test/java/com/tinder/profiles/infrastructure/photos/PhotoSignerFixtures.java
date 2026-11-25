package com.tinder.profiles.infrastructure.photos;

import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import com.tinder.profiles.application.photos.model.StoredPhotoMedia;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test doubles for photo URL signing through the photos service. */
public final class PhotoSignerFixtures {

    /** Base the fake photos service signs under, shaped like local-storage URLs. */
    public static final String MEDIA_BASE = "https://api.lunari.test/media/";

    private PhotoSignerFixtures() {
    }

    public static String signedUrl(UUID ownerId, String storageId, String variant) {
        return MEDIA_BASE + "photos/%s/%s/%s.jpg?exp=1700000300&sig=test".formatted(ownerId, storageId, variant);
    }

    /** A signer whose photos service signs every ref it is asked for. */
    public static PhotoDownloadUrlSigner signingSigner() {
        return new PhotoDownloadUrlSigner(new RecordingPhotoMedia());
    }

    /**
     * Fake photos service: signs every requested variant as {@link #signedUrl} and
     * records each batch, or throws {@link #failWith} when set.
     */
    public static final class RecordingPhotoMedia implements PhotoMediaPort {

        public final List<List<PhotoVariantRef>> batches = new ArrayList<>();
        public RuntimeException failWith;

        @Override
        public Map<PhotoVariantRef, String> presignedDownloadUrls(Collection<PhotoVariantRef> refs) {
            batches.add(List.copyOf(refs));
            if (failWith != null) {
                throw failWith;
            }
            Map<PhotoVariantRef, String> urls = new LinkedHashMap<>();
            refs.forEach(ref -> urls.put(ref, signedUrl(ref.ownerId(), ref.storageId(), ref.variant())));
            return urls;
        }

        @Override
        public StoredPhotoMedia store(UUID ownerId, byte[] image, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID ownerId, String storageId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String presignedDownloadUrl(UUID ownerId, String storageId, String variant) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int cleanupOrphans(UUID ownerId, Collection<String> cataloguedStorageIds) {
            throw new UnsupportedOperationException();
        }
    }
}
