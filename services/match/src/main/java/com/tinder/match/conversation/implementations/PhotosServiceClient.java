package com.tinder.match.conversation.implementations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class PhotosServiceClient {

    private static final String NAMESPACE = "chat/photos";

    private final RestClient photosRestClient;

    public PhotosServiceClient(@Qualifier("photosRestClient") RestClient photosRestClient) {
        this.photosRestClient = photosRestClient;
    }

    public PhotosUploadResponse upload(UUID ownerId, byte[] image, String contentType, String originalFilename) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new NamedByteArrayResource(image, originalFilename == null ? "photo" : originalFilename))
                .contentType(contentType == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(contentType));
        builder.part("owner_id", ownerId.toString());
        builder.part("namespace", NAMESPACE);

        try {
            PhotosUploadResponse response = photosRestClient.post()
                    .uri("/api/v1/photos")
                    .body(builder.build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw mapStatus(httpResponse.getStatusCode(), readBody(httpResponse));
                    })
                    .body(PhotosUploadResponse.class);
            if (response == null) {
                throw new IllegalStateException("Photos service returned an empty upload response");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapStatus(exception.getStatusCode(), exception.getResponseBodyAsString());
        }
    }

    private RuntimeException mapStatus(HttpStatusCode status, String body) {
        String message = extractMessage(body);
        if (status.is4xxClientError()) {
            return new IllegalArgumentException(message);
        }
        return new IllegalStateException(message);
    }

    private String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try {
            return new String(response.getBody().readAllBytes());
        } catch (Exception exception) {
            return "";
        }
    }

    private String extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return "Photos service request failed";
        }
        int start = body.indexOf("\"message\"");
        if (start < 0) {
            return body;
        }
        int colon = body.indexOf(':', start);
        int firstQuote = body.indexOf('"', colon + 1);
        int secondQuote = body.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) {
            return body;
        }
        return body.substring(firstQuote + 1, secondQuote);
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

    public record PhotosUploadResponse(
            String storageId,
            String originalUrl,
            String largeUrl,
            String mediumUrl,
            String smallUrl,
            String originalKey,
            String contentType,
            Long size,
            Integer width,
            Integer height,
            String sha256
    ) {
    }
}
