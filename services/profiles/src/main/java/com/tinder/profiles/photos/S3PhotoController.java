package com.tinder.profiles.photos;

import com.tinder.profiles.profile.dto.errors.ErrorSummary;
import com.tinder.profiles.profile.dto.success.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
public class S3PhotoController {

    private final S3PhotoService service;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank(message = "missing key") String key,
            @RequestParam(required = false) String userId
    ) throws IOException {


        if (userId != null) {
            int photoCount = service.getPhotoCountForUser(userId);

            // Limit to 5 photos per user
            if (photoCount >= 5) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(ErrorSummary.builder()
                                .code("PHOTO_LIMIT_EXCEEDED")
                                .message("User has reached the maximum number 5 of photos allowed")
                                .build());
            }
            service.uploadAndSave(file.getBytes(), key, file.getContentType(), userId);
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success("Photo uploaded and saved successfully", Map.of("key", key)));
        }
        service.upload(file.getBytes(), key, file.getContentType());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Photo uploaded successfully", Map.of("key", key)));

    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@RequestParam @NotBlank(message = "missing key") String key) {
        byte[] file = service.download(key);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Photo download URL generated successfully", Map.of("url", file)));
    }

    @GetMapping("/get-photo-url")
    public ResponseEntity<?> getUrl(@RequestParam @NotBlank(message = "missing key") String key) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(service.getPhotoUrl(key));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(@RequestParam @NotBlank(message = "missing key") String key) {
        service.delete(key);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Photo deleted successfully"));
    }
}