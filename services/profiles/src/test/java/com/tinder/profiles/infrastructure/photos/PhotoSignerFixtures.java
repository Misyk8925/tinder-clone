package com.tinder.profiles.infrastructure.photos;

import com.tinder.profiles.config.props.PhotoProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

public final class PhotoSignerFixtures {

    private PhotoSignerFixtures() {
    }

    public static PhotoProperties testPhotos() {
        return new PhotoProperties(
                new PhotoProperties.S3("test-bucket", 300),
                new PhotoProperties.Cloudfront("", false),
                new PhotoProperties.Policy(
                        5, DataSize.ofMegabytes(5), List.of("image/jpeg"), 300, 4096));
    }
}
