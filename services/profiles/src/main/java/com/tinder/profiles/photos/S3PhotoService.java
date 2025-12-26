package com.tinder.profiles.photos;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3PhotoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final PhotoRepository photoRepository;
    private final ImageProcessingService imageProcessingService;
    private final ProfileService profileService;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presign-exp-seconds:3000}")
    private int presignExpSeconds;

    @Value("5")
    private int maxPhotosPerProfile;



    public PhotoUrls uploadProfilePhoto(MultipartFile file, Profile profile, String position) throws IOException {

        int iPosition = Integer.parseInt(position);
        List<Photo> existingPhotos = photoRepository.findAllByProfile_ProfileId(profile.getProfileId());
        if (existingPhotos.isEmpty()) {
            if (iPosition != 0) {
                throw new IllegalArgumentException("Invalid position: " + position);
            }
        }

        if (iPosition < existingPhotos.size()) {
            // Replace existing photo at this position
            Photo toReplace = existingPhotos.get(iPosition);
            String[] parts = toReplace.getS3Key().split("/");
            delete(parts[parts.length-1], UUID.fromString(profile.getUserId()));
            photoRepository.delete(toReplace);
        } else if (iPosition > existingPhotos.size()) {
            if (iPosition> maxPhotosPerProfile - 1) {
                throw new IllegalArgumentException("Maximum of " + maxPhotosPerProfile + " photos allowed per profile");
            }
        }
        String profileId = profile.getProfileId().toString();
        log.info("Processing photo upload for user: {}", profileId);

        // 1. Validate the image first
        imageProcessingService.validateImage(file);

        // 2. Process image into multiple sizes
        ImageProcessingService.ProcessedImages processed =
                imageProcessingService.processUploadedImage(file);

        // 3. Generate unique photo ID and S3 keys
        String photoId = UUID.randomUUID().toString();
        String baseKey = String.format("photos/%s/%s", profileId, photoId);

        // 4. Upload all versions to S3
        String originalKey = uploadToS3(processed.original(), baseKey + "/original.jpg", "image/jpeg");
        String largeKey = uploadToS3(processed.large(), baseKey + "/large.jpg", "image/jpeg");
        String mediumKey = uploadToS3(processed.medium(), baseKey + "/medium.jpg", "image/jpeg");
        String smallKey = uploadToS3(processed.small(), baseKey + "/small.jpg", "image/jpeg");

        log.info("Uploaded 4 versions of photo {} for user {}", photoId, profileId);

        // 5. Save metadata to database (using medium as primary reference)
        Photo photo = new Photo();
        photo.setS3Key(mediumKey); // Use medium as default reference
        photo.setContentType("image/jpeg");
        photo.setSize(processed.medium().length);
        photo.setUrl(getPublicUrl(mediumKey));
        photo.setPrimary(iPosition==0);
        photo.setCreatedAt(LocalDateTime.now());
        photo.setPosition(iPosition);
        // Note: You need to set the profile relationship here
        photo.setProfile(profile);


        photoRepository.save(photo);

        // 6. Return all URLs
        return new PhotoUrls(
                photoId,
                getPublicUrl(originalKey),
                getPublicUrl(largeKey),
                getPublicUrl(mediumKey),
                getPublicUrl(smallKey)
        );
    }

    /**
     * Legacy method - kept for backward compatibility
     * Consider migrating to uploadProfilePhoto() instead
     */
    @Deprecated
    public void uploadAndSave(byte[] data, String key, String contentType, String userId) {
        UUID uuid = UUID.fromString(userId);
        upload(data, key, contentType);

        Photo photo = new Photo();
        photo.setS3Key(key);
        photo.setContentType(contentType);
        photo.setSize(data.length);
        photo.setUrl(getPublicUrl(key));
        photo.setPrimary(false);
        photo.setCreatedAt(LocalDateTime.now());

        photoRepository.save(photo);
    }

    /**
     * Upload raw bytes to S3 (internal helper)
     */
    private String uploadToS3(byte[] data, String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .metadata(Map.of(
                        "x-origin", "spring-boot",
                        "uploaded-at", LocalDateTime.now().toString()
                ))
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.debug("Uploaded {} bytes to S3: {}", data.length, key);
        return key;
    }

    /**
     * Public method to upload raw bytes (use uploadProfilePhoto for images)
     */
    public String upload(byte[] data, String key, String contentType) {
        return uploadToS3(data, key, contentType != null ? contentType : "application/octet-stream");
    }

    /**
     * Download file from S3
     */
    public byte[] download(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (Exception e) {
            log.error("Failed to download from S3: {}", key, e);
            return null;
        }
    }

    /**
     * Get public URL for a photo (if bucket is public) or use presigned URL
     */
    public String getPhotoUrl(String key) {
        byte[] check = download(key);
        if (check == null) {
            return null;
        }
        return getPublicUrl(key);
    }

    /**
     * Generate public S3 URL
     */
    private String getPublicUrl(String key) {
        return String.format("https://%s.s3.amazonaws.com/%s", bucket, key);
    }

    /**
     * Generate presigned upload URL (for direct client uploads)
     */
    public URL presignUpload(String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpSeconds))
                .putObjectRequest(request)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url();
    }

    /**
     * Generate presigned download URL (for private photos)
     */
    public URL presignDownload(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignExpSeconds))
                .getObjectRequest(request)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url();
    }

    /**
     * Delete photo from S3 (deletes all versions if using new structure)
     */
    public void delete(String photoId, UUID userId) {
        String baseKey = String.format("photos/%s/%s", userId, photoId);

        // Delete all versions
        deleteFromS3(baseKey + "/original.jpg");
        deleteFromS3(baseKey + "/large.jpg");
        deleteFromS3(baseKey + "/medium.jpg");
        deleteFromS3(baseKey + "/small.jpg");

        log.info("Deleted all versions of photo {} for user {}", photoId, userId);
    }

    /**
     * Delete single key from S3 (internal helper)
     */
    private void deleteFromS3(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to delete from S3: {}", key, e);
        }
    }

    /**
     * Get photo count for user
     */
    public int getPhotoCountForUser(String userId) {
        UUID uuid = UUID.fromString(userId);
        return photoRepository.countByProfile_ProfileId(uuid);
    }

    /**
     * DTO for returning all photo URLs
     */
    public record PhotoUrls(
            String photoId,
            String originalUrl,
            String largeUrl,
            String mediumUrl,
            String smallUrl
    ) {}
}