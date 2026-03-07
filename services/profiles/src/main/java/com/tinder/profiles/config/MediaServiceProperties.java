package com.tinder.profiles.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "media.service")
public class MediaServiceProperties {
    private String baseUrl = "http://localhost:8040";
    private int readyPollMaxAttempts = 30;
    private long readyPollIntervalMs = 200;
}

