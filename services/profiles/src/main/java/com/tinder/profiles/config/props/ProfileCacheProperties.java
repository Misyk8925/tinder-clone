package com.tinder.profiles.config.props;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "profiles.cache")
public class ProfileCacheProperties {

    private JwtProfileId jwtProfileId = new JwtProfileId();
    private JwtToken jwtToken = new JwtToken();
    private SharedProfile sharedProfile = new SharedProfile();

    @Getter
    @Setter
    public static class JwtProfileId {
        private Duration ttl = Duration.ofMinutes(30);
        private long maxSize = 250_000;
    }

    @Getter
    @Setter
    public static class JwtToken {
        private Duration ttl = Duration.ofMinutes(5);
        private long maxSize = 250_000;
    }

    @Getter
    @Setter
    public static class SharedProfile {
        private Duration ttl = Duration.ofMinutes(30);
        private long maxSize = 250_000;
    }

}
