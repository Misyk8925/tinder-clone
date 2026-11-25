package com.tinder.profiles.infrastructure.photos;

import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import com.tinder.profiles.infrastructure.photos.PhotoDownloadUrlSigner.PhotoLocator;
import com.tinder.profiles.infrastructure.photos.PhotoSignerFixtures.RecordingPhotoMedia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;

@DisplayName("PhotoDownloadUrlSigner")
class PhotoDownloadUrlSignerTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final RecordingPhotoMedia photosService = new RecordingPhotoMedia();
    private final PhotoDownloadUrlSigner signer = new PhotoDownloadUrlSigner(photosService);

    @Test
    @DisplayName("Given catalogued keys and legacy URLs, when resolved, then one batch signs each distinct variant and legacy photos keep their stored url")
    void signsDistinctCataloguedVariantsInOneBatch() {
        // Given
        PhotoLocator original = locator("s-1", "original");
        PhotoLocator sameObjectAgain = new PhotoLocator(PROFILE_ID, original.s3Key(), "https://other/stored");
        PhotoLocator small = locator("s-2", "small");
        PhotoLocator legacy = new PhotoLocator(PROFILE_ID, "legacy/photo-0", "https://cdn/legacy/0");

        // When
        Map<PhotoLocator, String> urls = signer.forClient(List.of(original, sameObjectAgain, small, legacy));

        // Then
        then(photosService.batches).hasSize(1);
        then(photosService.batches.get(0)).containsExactlyInAnyOrder(
                new PhotoVariantRef(PROFILE_ID, "s-1", "original"),
                new PhotoVariantRef(PROFILE_ID, "s-2", "small"));
        then(urls.get(original)).isEqualTo(PhotoSignerFixtures.signedUrl(PROFILE_ID, "s-1", "original"));
        then(urls.get(sameObjectAgain)).isEqualTo(PhotoSignerFixtures.signedUrl(PROFILE_ID, "s-1", "original"));
        then(urls.get(small)).isEqualTo(PhotoSignerFixtures.signedUrl(PROFILE_ID, "s-2", "small"));
        then(urls.get(legacy)).isEqualTo("https://cdn/legacy/0");
    }

    @Test
    @DisplayName("Given only a stored S3 URL and no key, when resolved, then the object is located from the URL and signed by the photos service")
    void locatesObjectFromStoredUrlWhenKeyIsMissing() {
        // Given
        PhotoLocator photo = new PhotoLocator(PROFILE_ID, null,
                "https://lunari-photos-pending.s3.eu-north-1.amazonaws.com/photos/%s/s-3/medium.jpg".formatted(PROFILE_ID));

        // When
        String url = signer.forClient(List.of(photo)).get(photo);

        // Then
        then(url).isEqualTo(PhotoSignerFixtures.signedUrl(PROFILE_ID, "s-3", "medium"));
    }

    @Test
    @DisplayName("Given the photos service is unavailable, when resolved, then every photo falls back to its stored url instead of failing the read")
    void fallsBackToStoredUrlsWhenPhotosServiceFails() {
        // Given
        photosService.failWith = new PhotoStorageException("Photos service unavailable: timeout");
        PhotoLocator photo = new PhotoLocator(PROFILE_ID, "photos/%s/s-1/original.jpg".formatted(PROFILE_ID), "https://stored/1");
        PhotoLocator keyOnly = new PhotoLocator(PROFILE_ID, "photos/%s/s-2/original.jpg".formatted(PROFILE_ID), null);

        // When
        Map<PhotoLocator, String> urls = signer.forClient(List.of(photo, keyOnly));

        // Then
        then(urls.get(photo)).isEqualTo("https://stored/1");
        then(urls.get(keyOnly)).isEqualTo(keyOnly.s3Key());
    }

    @Test
    @DisplayName("Given no photos, when resolved, then the photos service is not called")
    void skipsPhotosServiceForNoPhotos() {
        // When
        Map<PhotoLocator, String> urls = signer.forClient(List.of());

        // Then
        then(urls).isEmpty();
        then(photosService.batches).isEmpty();
    }

    private static PhotoLocator locator(String storageId, String variant) {
        return new PhotoLocator(PROFILE_ID,
                "photos/%s/%s/%s.jpg".formatted(PROFILE_ID, storageId, variant),
                "https://lunari-photos-pending.s3.eu-north-1.amazonaws.com/photos/%s/%s/%s.jpg"
                        .formatted(PROFILE_ID, storageId, variant));
    }
}
