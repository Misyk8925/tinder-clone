package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Retention of soft-deleted profiles ({@code profile.cleanup.*}). The scan
 * interval itself is read by {@code @Scheduled} from
 * {@code profile.cleanup.check-interval-ms}.
 */
@ConfigurationProperties(prefix = "profile.cleanup")
public record ProfileCleanupProperties(@DefaultValue("30") int retentionDays) {
}
