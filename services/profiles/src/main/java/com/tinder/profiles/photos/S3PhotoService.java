package com.tinder.profiles.photos;

import com.tinder.profiles.profile.Profile;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3PhotoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final PhotoRepository photoRepository;
    private final ImageProcessingService imageProcessingService;

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.presign-exp-seconds:3000}")
    private int presignExpSeconds;

    @Value("5")
    private int maxPhotosPerProfile;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Value("${app.cloudfront.enabled:false}")
    private boolean cloudfrontEnabled;

    @Value("${cloud.aws.region}")
    private String region;



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

            log.debug("Replacing photo at position {}, Photo ID: {}, S3 key: {}",
                     iPosition, toReplace.getPhotoID(), toReplace.getS3Key());

            // Primary method: Use the Photo's UUID directly
            String photoId = toReplace.getPhotoID().toString();
            log.debug("Using photoId from database: {}", photoId);

            delete(photoId, UUID.fromString(profile.getUserId()));

            // Fallback method: Also delete based on s3Key path to handle legacy records
            try {
                String s3KeyPhotoId = extractPhotoIdFromS3Key(toReplace.getS3Key());
                if (!s3KeyPhotoId.equals(photoId)) {
                    log.info("S3 key contains different photoId: {}, also deleting it", s3KeyPhotoId);
                    delete(s3KeyPhotoId, UUID.fromString(profile.getUserId()));
                }
            } catch (Exception e) {
                log.warn("Could not extract photoId from s3Key: {}, continuing with database photoId only",
                        toReplace.getS3Key(), e);
            }

            photoRepository.delete(toReplace);
        } else if (iPosition > existingPhotos.size()) {
            if (iPosition> maxPhotosPerProfile - 1) {
                throw new IllegalArgumentException("Maximum of " + maxPhotosPerProfile + " photos allowed per profile");
            }
        }
        String profileId = profile.getProfileId().toString();
        log.info("Processing photo upload for user: {}", profileId);

        // Clean up any orphaned photos before processing new upload
        cleanupOrphanedPhotos(UUID.fromString(profileId));

        // Read bytes once to avoid stream exhaustion
        byte[] imageBytes = file.getBytes();

        // 1. Validate content type and file size
        String contentType = file.getContentType();
        if (!List.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type" + (contentType == null ? "" : ": " + contentType));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Image too large (" + file.getSize() + " bytes)");
        }

        // 2. Validate image content and dimensions using bytes
        imageProcessingService.validateImageBytes(imageBytes);

        // 3. Process image into multiple sizes (using same cached bytes)
        ImageProcessingService.ProcessedImages processed =
                imageProcessingService.processUploadedImage(imageBytes);

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
        if (cloudfrontEnabled && !cloudfrontDomain.isEmpty()) {
            // Use CloudFront URL
            return cloudfrontDomain + "/" + key;
        } else {
            // Fallback to S3 URL
            return String.format("https://%s.s3.%s.amazonaws.com/%s",
                    bucket, region, key);
        }
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
        log.info("Deleting photo {} for user {}", photoId, userId);
        String baseKey = String.format("photos/%s/%s", userId, photoId);

        // Delete all versions
        deleteFromS3(baseKey + "/original.jpg");
        deleteFromS3(baseKey + "/large.jpg");
        deleteFromS3(baseKey + "/medium.jpg");
        deleteFromS3(baseKey + "/small.jpg");

        log.info("Deleted all versions of photo {} for user {}", photoId, userId);
    }

    /**
     * Clean up all orphaned photos for a user
     * This method lists all S3 objects for a user and deletes those not in database
     */
    public void cleanupOrphanedPhotos(UUID userId) {
        try {
            log.info("Cleaning up orphaned photos for user: {}", userId);

            // Get all photos for user from database
            List<Photo> dbPhotos = photoRepository.findAllByProfile_ProfileId(userId);
            Set<String> validPhotoIds = dbPhotos.stream()
                .map(photo -> photo.getPhotoID().toString())
                .collect(java.util.stream.Collectors.toSet());

            // List all objects in S3 for this user
            String userPrefix = String.format("photos/%s/", userId);

            software.amazon.awssdk.services.s3.model.ListObjectsV2Request listRequest =
                software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(userPrefix)
                    .build();

            software.amazon.awssdk.services.s3.model.ListObjectsV2Response listResponse =
                s3Client.listObjectsV2(listRequest);

            int deletedCount = 0;
            Set<String> processedPhotoIds = new java.util.HashSet<>();

            for (software.amazon.awssdk.services.s3.model.S3Object s3Object : listResponse.contents()) {
                String key = s3Object.key();
                // Extract photoId from key: photos/userId/photoId/filename.jpg
                String[] parts = key.split("/");
                if (parts.length >= 3) {
                    String s3PhotoId = parts[2];
                    if (!validPhotoIds.contains(s3PhotoId) && !processedPhotoIds.contains(s3PhotoId)) {
                        log.info("Found orphaned photo {} for user {}, deleting", s3PhotoId, userId);
                        delete(s3PhotoId, userId);
                        deletedCount++;
                        processedPhotoIds.add(s3PhotoId);
                    }
                }
            }

            log.info("Cleanup completed for user {}: deleted {} orphaned photos", userId, deletedCount);

        } catch (Exception e) {
            log.error("Failed to cleanup orphaned photos for user {}", userId, e);
        }
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
     * Extract photo ID from S3 key or URL
     * Handles both formats:
     * - S3 key: "photos/userId/photoId/medium.jpg"
     * - CloudFront URL: "https://domain.cloudfront.net/photos/userId/photoId/medium.jpg"
     * - S3 URL: "https://bucket.s3.region.amazonaws.com/photos/userId/photoId/medium.jpg"
     */
    private String extractPhotoIdFromS3Key(String s3KeyOrUrl) {
        if (s3KeyOrUrl == null || s3KeyOrUrl.isEmpty()) {
            throw new IllegalArgumentException("S3 key or URL cannot be null or empty");
        }

        log.debug("Extracting photoId from: {}", s3KeyOrUrl);

        // Remove protocol and domain if it's a full URL
        String path = s3KeyOrUrl;
        if (s3KeyOrUrl.startsWith("http")) {
            try {
                java.net.URL url = new java.net.URL(s3KeyOrUrl);
                path = url.getPath();
                // Remove leading slash from path
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                log.debug("Converted URL to path: {}", path);
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException("Invalid URL format: " + s3KeyOrUrl, e);
            }
        }

        // Now path should be like: "photos/userId/photoId/medium.jpg"
        String[] parts = path.split("/");
        log.debug("Split path into parts: {}", java.util.Arrays.toString(parts));

        if (parts.length < 4 || !parts[0].equals("photos")) {
            throw new IllegalArgumentException("Invalid S3 key format: " + path +
                ". Expected format: photos/userId/photoId/filename.jpg");
        }

        // parts[0] = "photos", parts[1] = userId, parts[2] = photoId, parts[3] = filename
        String extractedPhotoId = parts[2];
        log.debug("Extracted photoId: {}", extractedPhotoId);
        return extractedPhotoId;
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