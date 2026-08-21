package com.tinder.profiles.infrastructure.external.photos;

import com.tinder.profiles.application.photos.exception.PhotoStorageException;
import com.tinder.profiles.application.photos.exception.PhotoValidationException;
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
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class PhotosServiceAdapter implements PhotoMediaPort {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
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

        PhotosUploadResponse response =         photosWebClient.post()
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

    record CleanupRequest(UUID ownerId, List<String> cataloguedStorageIds, String namespace) {
    }

    record CleanupResponse(int deleted) {
    }

    record PhotoServiceError(String code, String message) {
    }
}
