package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.port.out.PhotoStoragePort;
import com.tinder.profiles.config.props.AwsProperties;
import com.tinder.profiles.config.props.PhotoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * S3 implementation of {@link PhotoStoragePort}. Knows buckets, presigning and
 * CloudFront — but nothing about slots, limits or which objects should exist:
 * those rules live in {@code application.photos}.
 */
@Component
@Slf4j
public class S3PhotoStorageAdapter implements PhotoStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String region;
    private final Duration presignDuration;
    private final PhotoProperties.Cloudfront cloudfront;

    public S3PhotoStorageAdapter(
            S3Client s3Client,
            S3Presigner s3Presigner,
            PhotoProperties photoProperties,
            AwsProperties awsProperties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = photoProperties.s3().bucket();
        this.presignDuration = Duration.ofSeconds(photoProperties.s3().presignExpSeconds());
        this.cloudfront = photoProperties.cloudfront();
        this.region = awsProperties.region();
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .metadata(Map.of(
                        "x-origin", "spring-boot",
                        "uploaded-at", LocalDateTime.now().toString()))
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(data));
        } catch (Exception e) {
            throw new PhotoStorageException("Failed to store object " + key, e);
        }
        log.debug("Uploaded {} bytes to S3: {}", data.length, key);
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("Failed to delete from S3: {}", key, e);
        }
    }

    @Override
    public List<String> listKeys(String prefix) {
        return s3Client.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().stream()
                .map(S3Object::key)
                .toList();
    }

    @Override
    public String publicUrl(String key) {
        if (cloudfront.servesTraffic()) {
            return cloudfront.domain() + "/" + key;
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
    }

    @Override
    public String presignedDownloadUrl(String key) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(presignDuration)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }
}
