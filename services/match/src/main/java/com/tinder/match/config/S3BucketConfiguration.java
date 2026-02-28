package com.tinder.match.config;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class S3BucketConfiguration {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider();

        var builder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider);

        String s3Endpoint = awsProperties.getS3().getEndpoint();
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(s3Endpoint))
                    .forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider();

        var builder = S3Presigner.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(credentialsProvider);

        String s3Endpoint = awsProperties.getS3().getEndpoint();
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(s3Endpoint));
        }

        return builder.build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider() {
        String accessKey = trimmedOrNull(awsProperties.getCredentials().getAccessKey());
        String secretKey = trimmedOrNull(awsProperties.getCredentials().getSecretKey());
        String sessionToken = trimmedOrNull(awsProperties.getCredentials().getSessionToken());

        boolean hasAccess = hasText(accessKey);
        boolean hasSecret = hasText(secretKey);

        if (hasAccess != hasSecret) {
            throw new IllegalStateException(
                    "Invalid AWS credential config: both cloud.aws.credentials.access-key and secret-key must be provided together"
            );
        }

        if (hasAccess) {
            if (hasText(sessionToken)) {
                log.info("Using static AWS session credentials from configuration");
                return StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(accessKey, secretKey, sessionToken)
                );
            }

            log.info("Using static AWS basic credentials from configuration");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
            );
        }

        log.info("Using AWS default credentials provider chain (env/profile/role)");
        return DefaultCredentialsProvider.create();
    }

    private String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
