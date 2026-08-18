package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Sizes and TTLs of the in-process caches ({@code profiles.cache.*}). Every
 * entry is a {@link CacheSpec}; per-cache values come from configuration.
 */
@ConfigurationProperties(prefix = "profiles.cache")
public record ProfileCacheProperties(

        @DefaultValue CacheSpec jwtProfileId,

        @DefaultValue CacheSpec jwtToken,

        @DefaultValue CacheSpec sharedProfile
) {

    public record CacheSpec(
            @DefaultValue("30m") Duration ttl,
            @DefaultValue("250000") long maxSize
    ) {
    }
}
