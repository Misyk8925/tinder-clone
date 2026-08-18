package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Photo storage, CDN and upload-policy settings ({@code app.*}).
 *
 * <p>{@link Policy} carries the upload rules the application layer enforces; the
 * rest describes where bytes are written and how they are served.
 */
@ConfigurationProperties(prefix = "app")
public record PhotoProperties(

        @DefaultValue S3 s3,

        @DefaultValue Cloudfront cloudfront,

        @DefaultValue Policy photos
) {

    public record S3(
            @DefaultValue("") String bucket,
            @DefaultValue("300") int presignExpSeconds
    ) {
    }

    public record Cloudfront(
            @DefaultValue("") String domain,
            @DefaultValue("false") boolean enabled
    ) {

        public boolean servesTraffic() {
            return enabled && domain != null && !domain.isBlank();
        }
    }

    public record Policy(
            @DefaultValue("5") int maxPerProfile,
            @DefaultValue("5MB") DataSize maxSize,
            @DefaultValue({"image/jpeg", "image/png", "image/webp"}) List<String> allowedContentTypes,
            @DefaultValue("300") int minDimensionPx,
            @DefaultValue("4096") int maxDimensionPx
    ) {
    }
}
