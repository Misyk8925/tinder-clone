package com.tinder.match.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "moderation")
public record ModerationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue Service service
) {
    public record Service(
            @DefaultValue("http://localhost:8086") String url,
            @DefaultValue("2s") Duration timeout,
            String username,
            String password
    ) {
    }
}
