package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
import com.tinder.profiles.application.photos.model.PhotoVariantRef;
import com.tinder.profiles.application.photos.model.StoredPhotoMedia;
import com.tinder.profiles.application.photos.port.out.PhotoMediaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class PhotosServiceAdapter implements PhotoMediaPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    // Signing sits on profile reads (GET /me, deck snapshots), which fall back to the
    // stored URL on failure; waiting the upload-sized 30 s there would stall the page.
    private static final Duration SIGNING_TIMEOUT = Duration.ofSeconds(3);
    private static final String NAMESPACE = "photos";

    private final WebClient photosWebClient;

    public PhotosServiceAdapter(@Qualifier("photosWebClient") WebClient photosWebClient) {
        this.photosWebClient = photosWebClient;
    }

    @Override
    public StoredPhotoMedia store(UUID ownerId, byte[] image, String contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new NamedByteArrayResource(image, "upload.bin"))
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType));
        builder.part("owner_id", ownerId.toString());
        builder.part("namespace", NAMESPACE);

        PhotosUploadResponse response = photosWebClient.post()
                .uri("/api/v1/photos")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toPhotoException)
                .bodyToMono(PhotosUploadResponse.class)
                .timeout(TIMEOUT)
                .onErrorMap(this::mapUnexpected)
                .block();

        if (response == null) {
            throw new PhotoStorageException("Photos service returned an empty upload response");
        }
        log.debug("Stored photo {} for owner {}", response.storageId(), ownerId);
        return new StoredPhotoMedia(
                response.storageId(),
                response.originalKey(),
                response.originalUrl(),
                response.largeUrl(),
                response.mediumUrl(),
                response.smallUrl(),
                response.contentType(),
                response.size());
    }

    @Override
    public void delete(UUID ownerId, String storageId) {
        photosWebClient.delete()
                .uri(uri -> uri.path("/api/v1/photos/{storageId}")
                        .queryParam("owner_id", ownerId)
                        .queryParam("namespace", NAMESPACE)
                        .build(storageId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toPhotoException)
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .onErrorMap(this::mapUnexpected)
                .block();
    }

    @Override
    public String presignedDownloadUrl(UUID ownerId, String storageId, String variant) {
        DownloadUrlResponse response = photosWebClient.get()
                .uri(uri -> uri.path("/api/v1/photos/{storageId}/download-url")
                        .queryParam("owner_id", ownerId)
                        .queryParam("size", variant)
                        .queryParam("namespace", NAMESPACE)
                        .build(storageId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toPhotoException)
                .bodyToMono(DownloadUrlResponse.class)
                .timeout(TIMEOUT)
                .onErrorMap(this::mapUnexpected)
                .block();
        if (response == null || response.url() == null) {
            throw new PhotoStorageException("Photos service returned an empty download URL");
        }
        return response.url();
    }

    @Override
    public Map<PhotoVariantRef, String> presignedDownloadUrls(Collection<PhotoVariantRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return Map.of();
        }
        List<DownloadUrlItem> items = refs.stream()
                .distinct()
                .map(ref -> new DownloadUrlItem(ref.ownerId(), ref.storageId(), ref.variant(), NAMESPACE))
                .toList();

        DownloadUrlsResponse response = photosWebClient.post()
                .uri("/api/v1/photos/download-urls")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DownloadUrlsRequest(items))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toPhotoException)
                .bodyToMono(DownloadUrlsResponse.class)
                .timeout(SIGNING_TIMEOUT)
                .onErrorMap(this::mapUnexpected)
                .block();

        if (response == null || response.items() == null) {
            return Map.of();
        }
        Map<PhotoVariantRef, String> urls = new HashMap<>(response.items().size());
        for (DownloadUrlResult item : response.items()) {
            if (item.url() != null) {
                urls.put(new PhotoVariantRef(item.ownerId(), item.storageId(), item.size()), item.url());
            }
        }
        return urls;
    }

    @Override
    public int cleanupOrphans(UUID ownerId, Collection<String> cataloguedStorageIds) {
        CleanupResponse response = photosWebClient.post()
                .uri("/api/v1/photos/cleanup-orphaned")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CleanupRequest(ownerId, List.copyOf(cataloguedStorageIds), NAMESPACE))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::toPhotoException)
                .bodyToMono(CleanupResponse.class)
                .timeout(TIMEOUT)
                .onErrorMap(this::mapUnexpected)
                .block();
        return response == null ? 0 : response.deleted();
    }

    private Mono<? extends Throwable> toPhotoException(ClientResponse response) {
        return response.bodyToMono(PhotoServiceError.class)
                .defaultIfEmpty(new PhotoServiceError("PHOTO_STORAGE_ERROR", "Photos service request failed"))
                .map(body -> {
                    String message = body.message() == null ? "Photos service request failed" : body.message();
                    if (response.statusCode().value() == 400) {
                        return new PhotoValidationException(message);
                    }
                    return new PhotoStorageException(message);
                });
    }

    private Throwable mapUnexpected(Throwable error) {
        if (error instanceof PhotoValidationException || error instanceof PhotoStorageException) {
            return error;
        }
        return new PhotoStorageException("Photos service unavailable: " + error.getMessage(), error);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    record PhotosUploadResponse(
            String storageId,
            String originalUrl,
            String largeUrl,
            String mediumUrl,
            String smallUrl,
            String originalKey,
            String contentType,
            long size,
            int width,
            int height,
            String sha256
    ) {
    }

    record DownloadUrlResponse(String url) {
    }

    record DownloadUrlItem(UUID ownerId, String storageId, String size, String namespace) {
    }

    record DownloadUrlsRequest(List<DownloadUrlItem> items) {
    }

    record DownloadUrlResult(UUID ownerId, String storageId, String size, String url) {
    }

    record DownloadUrlsResponse(List<DownloadUrlResult> items) {
    }

    record CleanupRequest(UUID ownerId, List<String> cataloguedStorageIds, String namespace) {
    }

    record CleanupResponse(int deleted) {
    }

    record PhotoServiceError(String code, String message) {
    }
}
