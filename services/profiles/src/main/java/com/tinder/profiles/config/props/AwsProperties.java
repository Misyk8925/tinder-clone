package com.tinder.profiles.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** AWS account and S3 endpoint settings ({@code cloud.aws.*}). */
@ConfigurationProperties(prefix = "cloud.aws")
public record AwsProperties(

        @DefaultValue("eu-north-1") String region,

        @DefaultValue Credentials credentials,

        @DefaultValue S3 s3
) {

    public record Credentials(
            @DefaultValue("placeholder-access-key") String accessKey,
            @DefaultValue("placeholder-secret-key") String secretKey
    ) {
    }

    /** {@code endpoint} is empty against real AWS and set only for LocalStack. */
    public record S3(@DefaultValue("") String endpoint) {

        public boolean hasCustomEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }
    }
}
