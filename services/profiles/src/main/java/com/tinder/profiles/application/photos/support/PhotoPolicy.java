package com.tinder.profiles.application.photos.support;

import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.ImageDimensions;

import java.util.List;

/**
 * The rules an uploaded photo must satisfy. A plain value object bound from
 * configuration in {@code config.application.ProfileApplicationConfig}, so the
 * use cases enforce the rules without reading properties themselves.
 */
public record PhotoPolicy(
        int maxPerProfile,
        long maxSizeBytes,
        List<String> allowedContentTypes,
        int minDimensionPx,
        int maxDimensionPx
) {

    public void requireAllowedContentType(String contentType) {
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new PhotoValidationException(
                    "Invalid image type" + (contentType == null ? "" : ": " + contentType));
        }
    }

    public void requireWithinSizeLimit(long sizeBytes) {
        if (sizeBytes > maxSizeBytes) {
            throw new PhotoValidationException("Image too large (" + sizeBytes + " bytes)");
        }
    }

    public void requireWithinDimensionLimits(ImageDimensions dimensions) {
        if (dimensions.width() < minDimensionPx || dimensions.height() < minDimensionPx) {
            throw new PhotoValidationException("Image too small");
        }
        if (dimensions.width() > maxDimensionPx || dimensions.height() > maxDimensionPx) {
            throw new PhotoValidationException("Image dimensions too large");
        }
    }

    /** Positions are zero-based, so the last usable slot is {@code maxPerProfile - 1}. */
    public void requireAssignablePosition(int position, int currentPhotoCount) {
        if (position < 0 || (position > currentPhotoCount && position > maxPerProfile - 1)) {
            throw new PhotoValidationException("Invalid position: " + position);
        }
    }

    public void requireFreeSlot(int currentPhotoCount) {
        if (currentPhotoCount >= maxPerProfile) {
            throw new PhotoValidationException(
                    "Maximum of " + maxPerProfile + " photos allowed per profile");
        }
    }
}
