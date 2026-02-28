package com.tinder.match.conversation.implementations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ConversationPhotoStorageService {

    private static final long MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024L;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final S3Client s3Client;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.cloudfront.domain:}")
    private String cloudfrontDomain;

    @Value("${app.cloudfront.enabled:false}")
    private boolean cloudfrontEnabled;

    @Value("${cloud.aws.region}")
    private String region;

    @Value("${cloud.aws.s3.endpoint:}")
    private String s3Endpoint;

    public ConversationPhotoStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public UploadedPhoto uploadPhoto(MultipartFile file, UUID conversationId, UUID senderId, UUID clientMessageId) {
        String resolvedBucket = requireBucket();
        validateFile(file);
        ensureBucketExistsIfLocal(resolvedBucket);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read photo bytes", exception);
        }

        BufferedImage image = readImage(bytes);

        String contentType = file.getContentType();
        String extension = fileExtensionFor(contentType);
        String key = String.format(
                "chat/photos/%s/%s/%s/%s.%s",
                conversationId,
                senderId,
                clientMessageId,
                UUID.randomUUID(),
                extension
        );

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(resolvedBucket)
                .key(key)
                .contentType(contentType)
                .metadata(Map.of(
                        "conversation-id", conversationId.toString(),
                        "sender-id", senderId.toString(),
                        "uploaded-at", Instant.now().toString()
                ))
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info(
                "Uploaded conversation photo conversationId={} senderId={} clientMessageId={} key={}",
                conversationId,
                senderId,
                clientMessageId,
                key
        );

        return new UploadedPhoto(
                key,
                getPublicUrl(resolvedBucket, key),
                contentType,
                (long) bytes.length,
                sanitizeOriginalName(file.getOriginalFilename()),
                image.getWidth(),
                image.getHeight(),
                sha256(bytes)
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Photo file is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type" + (contentType == null ? "" : ": " + contentType));
        }

        if (file.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new IllegalArgumentException("Image too large (" + file.getSize() + " bytes)");
        }
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Corrupted image");
            }

            if (image.getWidth() < 50 || image.getHeight() < 50) {
                throw new IllegalArgumentException("Image too small");
            }
            if (image.getWidth() > 6000 || image.getHeight() > 6000) {
                throw new IllegalArgumentException("Image dimensions too large");
            }

            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decode image", exception);
        }
    }

    private String getPublicUrl(String resolvedBucket, String key) {
        if (cloudfrontEnabled && cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            return cloudfrontDomain + "/" + key;
        }

        return String.format("https://%s.s3.%s.amazonaws.com/%s", resolvedBucket, region, key);
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Missing config: app.s3.bucket (or AWS_S3_BUCKET) must be set for photo uploads");
        }
        return bucket.trim();
    }

    private void ensureBucketExistsIfLocal(String resolvedBucket) {
        if (s3Endpoint == null || s3Endpoint.isBlank()) {
            return;
        }
        if (!ensuredBuckets.add(resolvedBucket)) {
            return;
        }

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(resolvedBucket).build());
        } catch (NoSuchBucketException exception) {
            createBucketIfMissing(resolvedBucket);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createBucketIfMissing(resolvedBucket);
            } else {
                throw exception;
            }
        }
    }

    private void createBucketIfMissing(String resolvedBucket) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(resolvedBucket).build());
            log.info("Created missing S3 bucket '{}' using endpoint {}", resolvedBucket, s3Endpoint);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // Bucket exists, safe to continue.
        }
    }

    private String fileExtensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "photo";
        }
        return originalName.replaceAll("[\\r\\n]", "_");
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record UploadedPhoto(
            String storageKey,
            String url,
            String mimeType,
            Long sizeBytes,
            String originalName,
            Integer width,
            Integer height,
            String sha256
    ) {
    }
}
