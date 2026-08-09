package com.tinder.profiles.application.photos.port.out;

import com.tinder.profiles.application.photos.model.ImageDimensions;
import com.tinder.profiles.application.photos.model.PhotoVariants;

import java.util.Optional;

/**
 * Outbound port for image decoding and resizing. Returns facts, never verdicts:
 * whether dimensions are acceptable is decided by {@code support.PhotoPolicy}.
 */
public interface ImageVariantsPort {

    /** Empty when the bytes cannot be decoded as an image. */
    Optional<ImageDimensions> probe(byte[] imageBytes);

    PhotoVariants render(byte[] imageBytes);
}
