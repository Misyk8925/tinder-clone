package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Photo storage, CDN and upload-policy settings ({@code app.*}).
 *
 * <p>{@link Policy} carries the upload rules the application layer enforces; the
 * rest describes how they are served. Storage and URL signing belong to the
 * photos service.
 */
@ConfigurationProperties(prefix = "app")
public record PhotoProperties(

        @DefaultValue Cloudfront cloudfront,

        @DefaultValue Policy photos
) {

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
