package com.tinder.profiles.api.photos;

import com.tinder.profiles.api.profile.dto.success.ApiResponse;
import com.tinder.profiles.application.photos.command.DeletePhotoCommand;
import com.tinder.profiles.application.photos.command.UploadPhotoCommand;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.UploadedPhoto;
import com.tinder.profiles.application.photos.usecase.CleanupOrphanedPhotosService;
import com.tinder.profiles.application.photos.usecase.CreatePhotoDownloadUrlService;
import com.tinder.profiles.application.photos.usecase.DeletePhotoService;
import com.tinder.profiles.application.photos.usecase.UploadPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP adapter for profile photos. Reads the multipart request, delegates to the
 * photo use cases and lets {@link PhotoExceptionHandler} translate failures.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profiles/photos")
@RequiredArgsConstructor
public class ProfilePhotoController {

    private final UploadPhotoService uploadPhoto;
    private final DeletePhotoService deletePhoto;
    private final CreatePhotoDownloadUrlService createDownloadUrl;
    private final CleanupOrphanedPhotosService cleanupOrphaned;

    /** Uploads a photo into the given slot, creating the four size variants. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadedPhoto>> uploadProfilePhoto(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String position
    ) throws IOException {
        UploadedPhoto uploaded = uploadPhoto.handle(new UploadPhotoCommand(
                jwt.getSubject(),
                file.getBytes(),
                file.getContentType(),
                file.getSize(),
                parsePosition(position)));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created("Photo uploaded successfully", uploaded));
    }

    /** Deletes a photo and all of its size variants. */
    @DeleteMapping("/{photoId}")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable UUID photoId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        deletePhoto.handle(new DeletePhotoCommand(jwt.getSubject(), photoId));
        return ResponseEntity.ok(ApiResponse.success("Photo deleted successfully"));
    }

    @GetMapping("/{photoId}/download-url")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
            @PathVariable String photoId,
            @RequestParam(defaultValue = "medium") String size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String url = createDownloadUrl.handle(jwt.getSubject(), photoId, size);
        return ResponseEntity.ok(ApiResponse.success("URL generated", url));
    }

    @PostMapping("/cleanup-orphaned")
    public ResponseEntity<ApiResponse<Void>> cleanupOrphanedPhotos(@AuthenticationPrincipal Jwt jwt) {
        cleanupOrphaned.handle(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Orphaned photos cleanup completed", null));
    }

    private int parsePosition(String position) {
        try {
            return Integer.parseInt(position);
        } catch (NumberFormatException e) {
            throw new PhotoValidationException("Invalid position: " + position);
        }
    }
}
