package com.tinder.profiles.application.photos.support;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

/**
 * The stored layout is {@code photos/{profileId}/{storageId}/{variant}.jpg}, and
 * older rows hold full CloudFront/S3 URLs instead of bare keys — both must yield
 * the same storage id.
 */
@DisplayName("PhotoKeys")
class PhotoKeysTest {

    private static final UUID PROFILE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String STORAGE_ID = "99999999-8888-7777-6666-555555555555";

    @Test
    @DisplayName("builds prefix, base and variant keys")
    void buildsKeys() {
        then(PhotoKeys.profilePrefix(PROFILE_ID)).isEqualTo("photos/" + PROFILE_ID + "/");
        then(PhotoKeys.baseKey(PROFILE_ID, STORAGE_ID))
                .isEqualTo("photos/" + PROFILE_ID + "/" + STORAGE_ID);
        then(PhotoKeys.variantKey(PROFILE_ID, STORAGE_ID, "medium"))
                .isEqualTo("photos/" + PROFILE_ID + "/" + STORAGE_ID + "/medium.jpg");
        then(PhotoKeys.allVariantKeys(PROFILE_ID, STORAGE_ID)).hasSize(4);
    }

    @Test
    @DisplayName("rejects unknown variants")
    void rejectsUnknownVariants() {
        thenThrownBy(() -> PhotoKeys.variantKey(PROFILE_ID, STORAGE_ID, "gigantic"))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Unknown photo size: gigantic");
    }

    @Test
    @DisplayName("recovers the storage id from a stored key")
    void recoversStorageIdFromKey() {
        then(PhotoKeys.storageIdOf(PhotoKeys.variantKey(PROFILE_ID, STORAGE_ID, "original")))
                .isEqualTo(STORAGE_ID);
    }

    @Test
    @DisplayName("recovers the storage id from CDN and bucket URLs")
    void recoversStorageIdFromUrls() {
        then(PhotoKeys.storageIdOf(
                "https://d123.cloudfront.net/photos/%s/%s/medium.jpg".formatted(PROFILE_ID, STORAGE_ID)))
                .isEqualTo(STORAGE_ID);
        then(PhotoKeys.storageIdOf(
                "https://bucket.s3.eu-north-1.amazonaws.com/photos/%s/%s/small.jpg"
                        .formatted(PROFILE_ID, STORAGE_ID)))
                .isEqualTo(STORAGE_ID);
    }

    @Test
    @DisplayName("rejects keys that do not follow the layout")
    void rejectsForeignKeys() {
        thenThrownBy(() -> PhotoKeys.storageIdOf("avatars/whatever.jpg"))
                .isInstanceOf(PhotoValidationException.class)
                .hasMessageContaining("Invalid S3 key format");
        thenThrownBy(() -> PhotoKeys.storageIdOf(""))
                .isInstanceOf(PhotoValidationException.class);
        thenThrownBy(() -> PhotoKeys.storageIdOf("http://:::"))
                .isInstanceOf(PhotoValidationException.class);
    }
}
