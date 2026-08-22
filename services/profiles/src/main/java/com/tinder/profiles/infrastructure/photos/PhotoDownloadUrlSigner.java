package com.tinder.profiles.infrastructure.photos;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.support.PhotoKeys;
import com.tinder.profiles.config.props.PhotoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Turns a catalogued object key into a short-lived GET URL the browser can load
 * against a private bucket. Stored {@code photo.url} values stay as durable
 * public/CDN locators; this is applied only when mapping a photo for a client.
 */
@Component
@Slf4j
public class PhotoDownloadUrlSigner {

    private final S3Presigner presigner;
    private final PhotoProperties photos;

    public PhotoDownloadUrlSigner(S3Presigner presigner, PhotoProperties photos) {
        this.presigner = presigner;
        this.photos = photos;
    }

    public String forClient(UUID profileId, String s3Key, String storedUrl) {
        String fallback = storedUrl == null ? s3Key : storedUrl;
        String bucket = photos.s3().bucket();
        if (presigner == null || bucket == null || bucket.isBlank() || profileId == null) {
            return fallback;
        }
        String locator = (s3Key != null && !s3Key.isBlank()) ? s3Key : storedUrl;
        if (locator == null || locator.isBlank()) {
            return fallback;
        }
        try {
            String storageId = PhotoKeys.storageIdOf(locator);
            String variant = PhotoKeys.variantOf(locator);
            GetObjectRequest get = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(PhotoKeys.variantKey(profileId, storageId, variant))
                    .build();
            GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(Math.max(1, photos.s3().presignExpSeconds())))
                    .getObjectRequest(get)
                    .build();
            return presigner.presignGetObject(presign).url().toString();
        } catch (PhotoValidationException invalid) {
            log.debug("Photo locator is not a catalogued key; using stored url for profile {}", profileId);
            return fallback;
        } catch (RuntimeException failed) {
            log.warn("Failed to presign photo for profile {}; using stored url", profileId, failed);
            return fallback;
        }
    }
}
