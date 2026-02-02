// src/main/java/com/example/s3/S3Config.java
package com.tinder.profiles.photos;

import com.tinder.profiles.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3BucketConfiguration {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                awsProperties.getCredentials().getAccessKey(),
                awsProperties.getCredentials().getSecretKey()
        );

        var builder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));

        // For LocalStack testing
        String s3Endpoint = awsProperties.getS3().getEndpoint();
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(s3Endpoint))
                   .forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                awsProperties.getCredentials().getAccessKey(),
                awsProperties.getCredentials().getSecretKey()
        );

        var builder = S3Presigner.builder()
                .region(Region.of(awsProperties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));

        // For LocalStack testing
        String s3Endpoint = awsProperties.getS3().getEndpoint();
        if (s3Endpoint != null && !s3Endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(s3Endpoint));
        }

        return builder.build();
    }
}