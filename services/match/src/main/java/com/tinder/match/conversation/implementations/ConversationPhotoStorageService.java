package com.tinder.match.conversation.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String NAMESPACE = "chat/photos";

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

        String url = uploaded.originalUrl();
        try {
            url = photosServiceClient.presignedDownloadUrl(
                    conversationId, uploaded.storageId(), "original", NAMESPACE);
        } catch (RuntimeException failed) {
            log.warn(
                    "Failed to presign conversation photo conversationId={} key={}; using stored url",
                    conversationId,
                    uploaded.originalKey(),
                    failed
            );
        }

        log.info(
                "Uploaded conversation photo conversationId={} senderId={} clientMessageId={} key={}",
                conversationId,
                senderId,
                clientMessageId,
                uploaded.originalKey()
        );

        return new UploadedPhoto(
                uploaded.originalKey(),
                url,
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

    public String downloadUrl(UUID ownerId, String storageKey, String fallbackUrl) {
        if (storageKey == null || storageKey.isBlank()) {
            return fallbackUrl;
        }
        try {
            return photosServiceClient.presignedDownloadUrl(
                    ownerId, storageIdOf(storageKey), "original", NAMESPACE);
        } catch (RuntimeException failed) {
            log.warn("Failed to presign chat photo for owner {}; using stored url", ownerId, failed);
            return fallbackUrl;
        }
    }

    public Map<String, String> downloadUrls(UUID ownerId, List<PhotoRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return Map.of();
        }

        Map<String, PhotoRef> unique = new ConcurrentHashMap<>();
        for (PhotoRef ref : refs) {
            if (ref == null || ref.storageKey() == null || ref.storageKey().isBlank()) {
                continue;
            }
            unique.putIfAbsent(ref.storageKey(), ref);
        }

        Map<String, String> urls = new ConcurrentHashMap<>();
        unique.values().parallelStream().forEach(ref ->
                urls.put(ref.storageKey(), downloadUrl(ownerId, ref.storageKey(), ref.fallbackUrl())));
        return Map.copyOf(urls);
    }

    public record PhotoRef(String storageKey, String fallbackUrl) {
    }

    static String storageIdOf(String keyOrUrl) {
        String path = keyOrUrl;
        if (keyOrUrl.startsWith("http")) {
            path = URI.create(keyOrUrl).getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String[] parts = path.split("/");
        if (parts.length >= 5 && "chat".equals(parts[0]) && "photos".equals(parts[1])) {
            return parts[3];
        }
        if (parts.length >= 4 && "photos".equals(parts[0])) {
            return parts[2];
        }
        throw new IllegalArgumentException("Invalid chat photo key: " + keyOrUrl);
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
