package com.tinder.profiles.config.application;

import com.tinder.profiles.application.photos.support.PhotoPolicy;
import com.tinder.profiles.application.profile.support.LocationChangePolicy;
import com.tinder.profiles.application.profile.support.ProfileRetentionPolicy;
import com.tinder.profiles.config.props.LocationProperties;
import com.tinder.profiles.config.props.PhotoProperties;
import com.tinder.profiles.config.props.ProfileCleanupProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds configuration to the application layer's policy value objects.
 *
 * <p>The use cases take these as constructor arguments instead of reading
 * properties themselves, which keeps {@code application..} free of Spring
 * property annotations and trivially unit-testable.
 */
@Configuration
public class ProfileApplicationConfig {

    @Bean
    LocationChangePolicy locationChangePolicy(LocationProperties properties) {
        return new LocationChangePolicy(properties.change().thresholdKm());
    }

    @Bean
    ProfileRetentionPolicy profileRetentionPolicy(ProfileCleanupProperties properties) {
        return new ProfileRetentionPolicy(properties.retentionDays());
    }

    @Bean
    PhotoPolicy photoPolicy(PhotoProperties properties) {
        PhotoProperties.Policy policy = properties.photos();
        return new PhotoPolicy(
                policy.maxPerProfile(),
                policy.maxSize().toBytes(),
                policy.allowedContentTypes(),
                policy.minDimensionPx(),
                policy.maxDimensionPx());
    }
}
