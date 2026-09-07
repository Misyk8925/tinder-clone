package com.tinder.match.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "profiles")
public class ProfilesServiceProperties {

    private Service service = new Service();

    @Data
    public static class Service {
        private String url = "http://localhost:8010";
    }
}
