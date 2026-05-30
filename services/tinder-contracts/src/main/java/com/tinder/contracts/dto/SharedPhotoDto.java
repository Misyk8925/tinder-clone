package com.tinder.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single profile photo as returned by the profiles service and consumed by the deck builder.
 *
 * @param photoId     unique identifier of the photo
 * @param profileId   identifier of the owning profile
 * @param s3Key       S3 object key; use this to construct a pre-signed URL server-side
 * @param isPrimary   true if this is the profile's main display photo
 * @param position    display order; lower value appears first
 * @param url         pre-signed or CDN URL; may be null if the caller did not request hydration
 * @param contentType MIME type (e.g. {@code "image/jpeg"})
 * @param size        file size in bytes
 * @param createdAt   timestamp when the photo was uploaded (UTC)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SharedPhotoDto(
        @NotNull UUID photoId,
        @NotNull UUID profileId,
        @NotNull String s3Key,
        boolean isPrimary,
        int position,
        String url,
        String contentType,
        long size,
        LocalDateTime createdAt
) {}
