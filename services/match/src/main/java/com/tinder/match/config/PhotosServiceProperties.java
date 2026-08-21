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
    }
}
