package com.tinder.profiles.profile.cache;

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
    private DeckProfile deckProfile = new DeckProfile();
    private DeckPage deckPage = new DeckPage();
    private DeckHotPath deckHotPath = new DeckHotPath();

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

    @Getter
    @Setter
    public static class DeckProfile {
        private Duration ttl = Duration.ofMinutes(30);
        private long maxSize = 250_000;
    }

    @Getter
    @Setter
    public static class DeckPage {
        private boolean enabled = true;
        private Duration ttl = Duration.ofSeconds(60);
        private long maxSize = 100_000;
    }

    @Getter
    @Setter
    public static class DeckHotPath {
        private boolean enabled = true;
        private Duration ttl = Duration.ofSeconds(60);
        private long maxSize = 250_000;
        private int maxTokenLength = 8192;
    }
}
