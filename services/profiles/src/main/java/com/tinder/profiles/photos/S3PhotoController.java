package com.tinder.profiles.photos;

import com.tinder.profiles.profile.Profile;
import com.tinder.profiles.profile.ProfileService;
import com.tinder.profiles.profile.dto.errors.ErrorSummary;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class S3PhotoController {

    private final S3PhotoService photoService;
    private final ProfileService profileService;

    /**
     * Upload profile photo - creates 4 versions (original, large, medium, small)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProfilePhoto(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String position
            ) {

        try {
            // Get user ID from JWT
            UUID userId = UUID.fromString(jwt.getSubject());

            Profile profile = profileService.getByUserId(String.valueOf(userId));



            // Check photo limit (5 photos max per user)
            int photoCount = photoService.getPhotoCountForUser(profile.getProfileId().toString());
            if (photoCount >= 5) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ErrorSummary.builder()
                                .code("PHOTO_LIMIT_EXCEEDED")
                                .message("Maximum of 5 photos allowed per profile")
                                .build());
            }

            // Process and upload photo
            S3PhotoService.PhotoUrls urls = photoService.uploadProfilePhoto(file, profile, position);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.created("Photo uploaded successfully", urls));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid photo upload: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ErrorSummary.builder()
                            .code("INVALID_IMAGE")
                            .message(e.getMessage())
                            .build());

        } catch (IOException e) {
            log.error("Failed to process photo upload", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorSummary.builder()
                            .code("UPLOAD_FAILED")
                            .message("Failed to process image upload")
                            .build());
        }
    }

    /**
     * Delete photo (deletes all versions)
     */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<?> deletePhoto(
            @PathVariable String photoId,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            photoService.delete(photoId, userId);

            return ResponseEntity
                    .ok(ApiResponse.success("Photo deleted successfully"));

        } catch (Exception e) {
            log.error("Failed to delete photo: {}", photoId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorSummary.builder()
                            .code("DELETE_FAILED")
                            .message("Failed to delete photo")
                            .build());
        }
    }

    /**
     * Get presigned download URL for a photo
     */
    @GetMapping("/{photoId}/download-url")
    public ResponseEntity<?> getDownloadUrl(
            @PathVariable String photoId,
            @RequestParam(defaultValue = "medium") String size,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            String key = String.format("photos/%s/%s/%s.jpg", userId, photoId, size);

            var url = photoService.presignDownload(key);

            return ResponseEntity.ok(ApiResponse.success("URL generated", url.toString()));

        } catch (Exception e) {
            log.error("Failed to generate download URL", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorSummary.builder()
                            .code("URL_GENERATION_FAILED")
                            .message("Failed to generate download URL")
                            .build());
        }
    }
}