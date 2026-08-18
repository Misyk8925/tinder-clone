package com.tinder.profiles.application.profile.support;

import java.time.LocalDateTime;

/**
 * How long soft-deleted profiles are kept before they are purged for good.
 *
 * <p>Bound from configuration in {@code config.application.ProfileApplicationConfig}
 * so the purge use case receives a plain value rather than reading properties.
 */
public record ProfileRetentionPolicy(int retentionDays) {

    /** Profiles soft-deleted before this instant are eligible for purging. */
    public LocalDateTime cutoffFrom(LocalDateTime now) {
        return now.minusDays(retentionDays);
    }
}
