package com.tinder.match.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "cloud.aws")
public class AwsProperties {

    private String region;

    private Credentials credentials = new Credentials();

    private S3 s3 = new S3();

    @Data
    public static class Credentials {

        private String accessKey;

        private String secretKey;

        private String sessionToken;
    }

    @Data
    public static class S3 {

        private String endpoint;
    }
}
