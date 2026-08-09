package com.tinder.profiles.application.photos.model;

/** The JPEG renditions produced for one upload, in descending size order. */
public record PhotoVariants(byte[] original, byte[] large, byte[] medium, byte[] small) {

    public byte[] of(String variant) {
        return switch (variant) {
            case "original" -> original;
            case "large" -> large;
            case "medium" -> medium;
            case "small" -> small;
            default -> throw new IllegalArgumentException("Unknown variant: " + variant);
        };
    }
}
