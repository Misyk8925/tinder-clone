package com.tinder.match.conversation.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationPhotoStorageService {

    private static final long MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024L;

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final PhotosServiceClient photosServiceClient;

    public UploadedPhoto uploadPhoto(MultipartFile file, UUID conversationId, UUID senderId, UUID clientMessageId) {
        validateFile(file);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read photo bytes", exception);
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        PhotosServiceClient.PhotosUploadResponse uploaded = photosServiceClient.upload(
                conversationId,
                bytes,
                file.getContentType(),
                originalName
        );

        log.info(
                "Uploaded conversation photo conversationId={} senderId={} clientMessageId={} key={}",
                conversationId,
                senderId,
                clientMessageId,
                uploaded.originalKey()
        );

        return new UploadedPhoto(
                uploaded.originalKey(),
                uploaded.originalUrl(),
                uploaded.contentType(),
                uploaded.size(),
                originalName,
                uploaded.width(),
                uploaded.height(),
                uploaded.sha256()
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

    private String sanitizeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "photo";
        }
        return originalName.replaceAll("[\\r\\n]", "_");
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
