package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Photos service client settings ({@code photos.*}). */
@ConfigurationProperties(prefix = "photos")
public record PhotosServiceProperties(@DefaultValue Service service) {

    /**
     * @param internalAuthSecret shared secret presented to the photos service in
     *                           {@code X-Internal-Auth}; the photos service refuses
     *                           unauthenticated media requests.
     */
    public record Service(
            @DefaultValue("http://localhost:8070") String url,
            @DefaultValue("") String internalAuthSecret) {
    }
}
