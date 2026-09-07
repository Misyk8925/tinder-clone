package com.tinder.match.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "photos")
public class PhotosServiceProperties {

    private Service service = new Service();

    @Data
    public static class Service {
        private String url = "http://localhost:8070";
        /**
         * Shared secret presented to the photos service in {@code X-Internal-Auth}; the photos
         * service refuses unauthenticated media requests.
         */
        private String internalAuthSecret = "";
    }
}
